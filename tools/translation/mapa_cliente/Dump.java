import world.gregs.voidps.buffer.read.ArrayReader;
import world.gregs.voidps.cache.Cache;
import world.gregs.voidps.cache.CacheDelegate;
import world.gregs.voidps.cache.config.data.StructDefinition;
import world.gregs.voidps.cache.config.decoder.StructDecoder;
import world.gregs.voidps.cache.definition.data.*;
import world.gregs.voidps.cache.definition.decoder.*;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only dump of interfaces, client scripts, enums and structs to JSON lines. */
public class Dump {
    static String q(Object o) {
        if (o == null) return "null";
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        String s = o.toString();
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    static String arr(Object[] a) {
        if (a == null) return "null";
        StringJoiner j = new StringJoiner(",", "[", "]");
        for (Object o : a) j.add(q(o));
        return j.toString();
    }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        String out = args[1];
        Cache cache = new CacheDelegate(dir, null, null);

        // Interfaces
        InterfaceDefinitionFull[] ifaces = new InterfaceDecoderFull().load(cache);
        List<Field> hookFields = new ArrayList<>();
        for (Field f : InterfaceComponentDefinitionFull.class.getDeclaredFields()) {
            if (f.getType() == Object[].class && !f.getName().equals("information")) {
                f.setAccessible(true);
                hookFields.add(f);
            }
        }
        Field info = InterfaceComponentDefinitionFull.class.getDeclaredField("information");
        info.setAccessible(true);
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out + "/interfaces.jsonl"), StandardCharsets.UTF_8))) {
            for (InterfaceDefinitionFull def : ifaces) {
                if (def == null || def.getComponents() == null) continue;
                for (InterfaceComponentDefinitionFull c : def.getComponents()) {
                    StringBuilder b = new StringBuilder();
                    b.append("{\"i\":").append(def.getId());
                    b.append(",\"c\":").append(c.getId() & 0xffff);
                    b.append(",\"type\":").append(c.getType());
                    b.append(",\"parent\":").append(c.getParent());
                    b.append(",\"hidden\":").append(c.getHidden());
                    b.append(",\"font\":").append(c.getFontId());
                    b.append(",\"text\":").append(q(c.getText()));
                    b.append(",\"name\":").append(q(c.getName()));
                    b.append(",\"options\":").append(arr(c.getOptions()));
                    b.append(",\"useOption\":").append(q(c.getUseOption()));
                    b.append(",\"optionOverride\":").append(q(c.getOptionOverride()));
                    b.append(",\"hooks\":{");
                    StringJoiner hj = new StringJoiner(",");
                    Object[] inf = (Object[]) info.get(c);
                    if (inf != null) hj.add("\"information\":" + arr(inf));
                    for (Field f : hookFields) {
                        Object[] h = (Object[]) f.get(c);
                        if (h != null) hj.add(q(f.getName()) + ":" + arr(h));
                    }
                    b.append(hj).append("}}");
                    w.println(b);
                }
            }
        }

        // Client scripts
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out + "/scripts.jsonl"), StandardCharsets.UTF_8))) {
            for (int id : cache.archives(12)) {
                byte[] data = cache.data(12, id, 0, null);
                if (data == null) continue;
                ClientScriptDefinition d = new ClientScriptDefinition();
                d.setId(id);
                try {
                    new ClientScriptDecoder(false).readLoop(d, new ArrayReader(data));
                } catch (Exception e) {
                    System.err.println("script " + id + " " + e);
                    continue;
                }
                StringBuilder b = new StringBuilder();
                b.append("{\"id\":").append(id).append(",\"name\":").append(q(d.getName()));
                b.append(",\"ia\":").append(d.getIntArgumentCount()).append(",\"sa\":").append(d.getStringArgumentCount());
                b.append(",\"ins\":[");
                int[] ins = d.getInstructions();
                int[] io = d.getIntOperands();
                String[] so = d.getStringOperands();
                for (int k = 0; k < ins.length; k++) {
                    if (k > 0) b.append(',');
                    b.append('[').append(ins[k]).append(',').append(io == null ? 0 : io[k]);
                    if (so != null && so[k] != null) b.append(',').append(q(so[k]));
                    b.append(']');
                }
                b.append("]}");
                w.println(b);
            }
        }

        // Enums
        EnumDefinition[] enums = new EnumDecoder().load(cache);
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out + "/enums.jsonl"), StandardCharsets.UTF_8))) {
            for (EnumDefinition e : enums) {
                if (e == null || e.getMap() == null) continue;
                StringBuilder b = new StringBuilder();
                b.append("{\"id\":").append(e.getId()).append(",\"k\":").append(q(String.valueOf(e.getKeyType())))
                        .append(",\"v\":").append(q(String.valueOf(e.getValueType())))
                        .append(",\"def\":").append(q(e.getDefaultString())).append(",\"map\":{");
                StringJoiner j = new StringJoiner(",");
                for (Map.Entry<Integer, Object> en : e.getMap().entrySet()) j.add(q(String.valueOf(en.getKey())) + ":" + q(en.getValue()));
                b.append(j).append("}}");
                w.println(b);
            }
        }

        // Structs (string params only)
        StructDefinition[] structs = new StructDecoder().load(cache);
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out + "/structs.jsonl"), StandardCharsets.UTF_8))) {
            for (StructDefinition s : structs) {
                if (s == null || s.getParams() == null) continue;
                StringJoiner j = new StringJoiner(",");
                for (Map.Entry<Integer, Object> en : s.getParams().entrySet()) j.add(q(String.valueOf(en.getKey())) + ":" + q(en.getValue()));
                w.println("{\"id\":" + s.getId() + ",\"p\":{" + j + "}}");
            }
        }
        System.out.println("done");
    }
}
