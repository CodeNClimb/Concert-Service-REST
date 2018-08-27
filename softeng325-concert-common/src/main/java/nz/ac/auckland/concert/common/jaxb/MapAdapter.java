package nz.ac.auckland.concert.common.jaxb;

import nz.ac.auckland.concert.common.types.PriceBand;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapAdapter extends XmlAdapter<MapAdapter.AdaptedMap, Map<PriceBand, BigDecimal>> {

    public static class AdaptedMap {

        public List<Entry> entry = new ArrayList<>();


        public static class Entry {

            public PriceBand key;

            public BigDecimal value;

        }

    }

    @Override
    public Map<PriceBand, BigDecimal> unmarshal(AdaptedMap adaptedMap) throws Exception {
        Map<PriceBand, BigDecimal> map = new HashMap<>();
        for(AdaptedMap.Entry entry : adaptedMap.entry) {
            map.put(entry.key, entry.value);
        }
        return map;
    }

    @Override
    public AdaptedMap marshal(Map<PriceBand, BigDecimal> map) throws Exception {
        AdaptedMap adaptedMap = new AdaptedMap();
        for(Map.Entry<PriceBand, BigDecimal> mapEntry : map.entrySet()) {
            AdaptedMap.Entry entry = new AdaptedMap.Entry();
            entry.key = mapEntry.getKey();
            entry.value = mapEntry.getValue();
            adaptedMap.entry.add(entry);
        }
        return adaptedMap;
    }

}
