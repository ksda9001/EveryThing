package org.everything;
import com.sun.jna.HANDLE;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

public class NtfsUsnEnumerator {
    private final Kernel32 k32 = Kernel32.INSTANCE;

    public interface UsnRecordConsumer {
        void onRecord(long frn, long parentFrn, String fileName, boolean isDir);
    }
    public interface ProgressListener { void onProgress(int count); }
    public interface CompletionListener { void onComplete(String volume); }

    public void enumerateVolume(String volume, UsnRecordConsumer consumer,
                                ProgressListener progress, CompletionListener complete) {
        String device = "\\\\.\\" + volume;
        HANDLE hVol = k32.CreateFile(device,
                WinConst.GENERIC_READ,
                WinConst.FILE_SHARE_READ | WinConst.FILE_SHARE_WRITE,
                null, WinConst.OPEN_EXISTING,
                WinConst.FILE_FLAG_BACKUP_SEMANTICS, null);

        if (hVol == null) throw new RuntimeException("Open volume failed: " + volume);

        try {
            MFT_ENUM_DATA_V0 in = new MFT_ENUM_DATA_V0();
            in.StartFileReferenceNumber = 0L;
            in.LowUsn = 0L;
            in.HighUsn = Long.MAX_VALUE;
            in.write();

            final int bufSize = 4 * 1024 * 1024;
            Memory out = new Memory(bufSize);
            IntByReference bytesRet = new IntByReference();
            int count = 0;

            while (true) {
                boolean ok = k32.DeviceIoControl(hVol,
                        WinConst.FSCTL_ENUM_USN_DATA,
                        in.getPointer(), in.size(),
                        out, bufSize, bytesRet, (Pointer) null);
                int n = bytesRet.getValue();
                if (!ok || n == 0) break;

                parseUsnBuffer(out, n, consumer);
                count += 1000;
                if (progress != null) progress.onProgress(count);

                long nextStartFrn = out.getLong(0);
                in.StartFileReferenceNumber = nextStartFrn;
                in.write();
            }
            if (complete != null) complete.onComplete(volume);
        } finally {
            k32.CloseHandle(hVol);
        }
    }

    private void parseUsnBuffer(Memory buf, int len, UsnRecordConsumer consumer) {
        int offset = 8;
        while (offset + 60 <= len) {
            int recordLen = buf.getInt(offset);
            if (recordLen <= 0 || offset + recordLen > len) break;

            long frn = buf.getLong(offset + 8);
            long parentFrn = buf.getLong(offset + 16);
            int attrs = buf.getInt(offset + 36);
            boolean isDir = (attrs & WinConst.FILE_ATTRIBUTE_DIRECTORY) != 0;
            short nameLen = buf.getShort(offset + 56);
            short nameOff = buf.getShort(offset + 58);
            String fileName = readUtf16le(buf, offset + nameOff, nameLen);

            consumer.onRecord(frn, parentFrn, fileName, isDir);
            offset += recordLen;
        }
    }

    private String readUtf16le(Memory buf, int pos, int byteLen) {
        char[] cs = new char[byteLen / 2];
        for (int i = 0; i < cs.length; i++) cs[i] = buf.getChar(pos + i * 2);
        return new String(cs);
    }
}
