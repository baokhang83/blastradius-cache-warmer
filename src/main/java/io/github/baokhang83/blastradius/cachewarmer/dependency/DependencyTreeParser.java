package io.github.baokhang83.blastradius.cachewarmer.dependency;

import java.util.ArrayList;
import java.util.List;

/** Parses Maven dependency:tree lines into resolved coordinates. */
public class DependencyTreeParser {
    public List<DependencyCoordinate> parse(String tree) {
        List<DependencyCoordinate> result = new ArrayList<>();
        for (String line : tree.lines().toList()) {
            String value = line.replaceFirst("^\\[INFO]\\s+[|\\\\+\\- ]*", "").trim();
            String[] parts = value.split(":");
            if (parts.length == 5 || parts.length == 6) {
                result.add(new DependencyCoordinate(parts[0], parts[1], parts[2], parts.length == 6 ? parts[3] : "", parts[parts.length - 2], parts[parts.length - 1]));
            }
        }
        return result;
    }
}
