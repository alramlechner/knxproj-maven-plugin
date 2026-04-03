package name.lechners.knx.knxproj;

/**
 * Holds all parsed data for a single KNX group address entry.
 */
public final class GroupAddressEntry {

    private final int    rawAddress;
    private final String name;
    private final String hauptgruppe;
    private final String mittelgruppe;
    private final String datapointType;
    private final String description;

    public GroupAddressEntry(int rawAddress,
                             String name,
                             String hauptgruppe,
                             String mittelgruppe,
                             String datapointType,
                             String description) {
        this.rawAddress    = rawAddress;
        this.name          = name;
        this.hauptgruppe   = hauptgruppe;
        this.mittelgruppe  = mittelgruppe;
        this.datapointType = datapointType;
        this.description   = description;
    }

    public int getRawAddress() {
        return rawAddress;
    }

    public String getName() {
        return name;
    }

    public String getHauptgruppe() {
        return hauptgruppe;
    }

    public String getMittelgruppe() {
        return mittelgruppe;
    }

    public String getDatapointType() {
        return datapointType;
    }

    public String getDescription() {
        return description;
    }

    /** KNX 3-level address: main/middle/sub */
    public String getFormattedAddress() {
        int main   = (rawAddress >> 11) & 0x1F;
        int middle = (rawAddress >>  8) & 0x07;
        int sub    =  rawAddress        & 0xFF;
        return main + "/" + middle + "/" + sub;
    }
}
