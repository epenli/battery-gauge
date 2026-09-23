package local.battery.gauge;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public final class BatteryName {
    private static final Pattern PATTERN = Pattern.compile("^(HBB[0-9]+)SOC([0-9]{3})$");
    public final String id;
    public final int percent;
    private BatteryName(String id, int percent) { this.id = id; this.percent = percent; }
    public static BatteryName parse(String name) {
        if (name == null) return null;
        Matcher m = PATTERN.matcher(name);
        if (!m.matches()) return null;
        int percent = Integer.parseInt(m.group(2));
        return percent <= 100 ? new BatteryName(m.group(1), percent) : null;
    }
}
