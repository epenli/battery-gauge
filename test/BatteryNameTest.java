import local.battery.gauge.BatteryName;
public class BatteryNameTest {
    public static void main(String[] args) {
        BatteryName b=BatteryName.parse("HBB8903302475SOC033");
        if(b==null || b.percent!=33 || !b.id.equals("HBB8903302475")) throw new AssertionError("observed advertisement");
        for(String s:new String[]{null,"","HBB1SOC101","HBB1SOC-01","HBB1SOC33","midea","HBB1SOC033extra"})
            if(BatteryName.parse(s)!=null)throw new AssertionError("invalid advertisement accepted");
        if(BatteryName.parse("HBB1SOC000").percent!=0 || BatteryName.parse("HBB1SOC100").percent!=100)
            throw new AssertionError("endpoints");
        System.out.println("Advertisement parser: observed name, invalid input, 0 and 100 passed.");
    }
}
