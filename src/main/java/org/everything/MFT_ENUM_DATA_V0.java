package org.everything;
import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;

public class MFT_ENUM_DATA_V0 extends Structure {
    public long StartFileReferenceNumber;
    public long LowUsn;
    public long HighUsn;

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("StartFileReferenceNumber", "LowUsn", "HighUsn");
    }
}

public class READ_USN_JOURNAL_DATA_V0 extends Structure {
    public long StartUsn;
    public int ReasonMask;
    public int ReturnOnlyOnClose;
    public long Timeout;
    public long BytesToWaitFor;
    public long UsnJournalId;

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("StartUsn","ReasonMask","ReturnOnlyOnClose","Timeout","BytesToWaitFor","UsnJournalId");
    }
}
