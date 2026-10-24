import world.gregs.voidps.cache.Cache;
import world.gregs.voidps.cache.CacheDelegate;
import world.gregs.voidps.cache.DefinitionDecoder;
import world.gregs.voidps.cache.definition.decoder.*;
import world.gregs.voidps.cache.config.decoder.*;
import java.lang.reflect.Field;
import java.util.*;

/** Read-only count of names and options in item, npc, object, quest, quick chat and world map definitions. */
public class Count {
    static Object get(Object o, String f) {
        try { Field x = o.getClass().getDeclaredField(f); x.setAccessible(true); return x.get(o); } catch (Exception e) { return null; }
    }
    static boolean text(Object s) { return s instanceof String && ((String) s).replaceAll("<[^>]*>", "").matches(".*[A-Za-z]{2,}.*") && !s.equals("null"); }
    static void count(String label, Object[] defs, String... fields) {
        for (String f : fields) {
            Set<String> uniq = new LinkedHashSet<>();
            int n = 0;
            for (Object d : defs) {
                if (d == null) continue;
                Object v = get(d, f);
                if (v instanceof Object[]) { for (Object s : (Object[]) v) if (text(s)) { uniq.add((String) s); n++; } }
                else if (text(v)) { uniq.add((String) v); n++; }
            }
            List<String> ex = new ArrayList<>(uniq);
            System.out.println(label + "." + f + " total=" + n + " distinct=" + uniq.size() + " ex=" + ex.subList(0, Math.min(8, ex.size())));
        }
    }
    public static void main(String[] a) throws Exception {
        Cache cache = new CacheDelegate(a[0], null, null);
        count("item", new ItemDecoderFull().load(cache), "name", "options", "floorOptions");
        count("npc", new NPCDecoderFull().load(cache), "name", "options");
        count("object", new ObjectDecoderFull().load(cache), "name", "options");
        count("quest", new QuestDecoder().load(cache), "name", "listName");
        count("quickchat_phrase", new QuickChatPhraseDecoder().load(cache), "stringParts");
        count("quickchat_menu", new QuickChatOptionDecoder().load(cache), "optionText");
        try { count("worldmap", new WorldMapDetailsDecoder().load(cache), "name"); } catch (Throwable t) { System.out.println("worldmap " + t); }
    }
}
