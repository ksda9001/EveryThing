package org.everything;
import com.sun.jna.HANDLE;
import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;

public class UsnTailer implements Runnable {
    private final Kernel32 k32 = Kernel32.INSTANCE;
    private final String volume;
    private final IndexStore store;
    private volatile boolean running = true;

    public UsnTailer(String volume, IndexStore store) {
        this.volume = volume;
        this.store = store;
    }

    public void start() {
        new Thread(this, "usn-tail-" + volume).start();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        String device = "\\\\.\\" + volume;
        HANDLE hVol = k32.CreateFile(device, WinConst.GENERIC_READ,
                WinConst.FILE_SHARE_READ | WinConst.FILE_SHARE_WRITE,
                null, WinConst.OPEN_EXISTING, WinConst.FILE_FLAG_BACKUP_SEMANTICS, null);
        if (hVol == null) return;

        try {
            long startUsn = 0L;
            final int bufSize = 4 * 1024 * 1024;
            Memory out = new Memory(bufSize);
            IntByReference bytesRet = new IntByReference();

            while (running) {
                READ_USN_JOURNAL_DATA_V0 in = new READ_USN_JOURNAL_DATA_V0();
                in.StartUsn = startUsn;
                in.ReasonMask = 0xFFFFFFFF;
                in.ReturnOnlyOnClose = 0;
                in.Timeout = 1_000_000L;
                in.BytesToWaitFor = 0L;
                in.UsnJournalId = 0L;
                in.write();

                boolean ok = k32.DeviceIoControl(hVol, WinConst.FSCTL_READ_USN_JOURNAL,
                        in.getPointer(), in.size(),
                        out, bufSize, bytesRet, null);

                int n = bytesRet.getValue();
                if (ok && n > 8) {
                    parseAndApply(out, n);
                    startUsn = out.getLong(0);
                } else {
                    try { Thread.sleep(50L); } catch (InterruptedException ignored) {}
                }
            }
        } finally {
            k32.CloseHandle(hVol);
        }
    }

    private void parseAndApply(Memory buf, int len) {
        int offset = 8;
        while (offset + 60 <= len) {
            int recordLen = buf.getInt(offset);
            if (recordLen <= 0 || offset + recordLen > len) break;

            long frn = buf.getLong(offset + 8);
            long parentFrn = buf.getLong(offset + 16);
            int reason = buf.getInt(offset + 40);
            int attrs = buf.getInt(offset + 36);
            boolean isDir = (attrs & WinConst.FILE_ATTRIBUTE_DIRECTORY) != 0;
            short nameLen = buf.getShort(offset + 56);
            short nameOff = buf.getShort(offset + 58);
            String fileName = readUtf16le(buf, offset + nameOff, nameLen);

            if ((reason & 0x00000200) != 0) { // FILE_DELETE
                store.remove(frn);
            } else if ((reason & 0x00000100) != 0) { // FILE_CREATE
                store.upsert(frn, parentFrn, fileName, isDir, volume);
            } else if ((reason & 0x00001000) != 0) { // RENAME_OLD_NAME
                store.remove(frn);
            } else if ((reason & 0x00002000) != 0) { // RENAME_NEW_NAME
                store.upsert(frn, parentFrn, fileName, isDir, volume);
            } else {
                store.upsert(frn, parentFrn, fileName, isDir, volume);
            }

            offset += recordLen;
        }
    }

    private String readUtf16le(Memory buf, int pos, int byteLen) {
        char[] cs = new char[byteLen / 2];
        for (int i = 0; i < cs.length; i++) cs[i] = buf.getChar(pos + i * 2);
        return new String(cs);
    }
}
