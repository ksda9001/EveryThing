package org.everything;
import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface Kernel32 extends StdCallLibrary {
    Kernel32 INSTANCE = (Kernel32) Native.load("Kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);

    HANDLE CreateFile(String lpFileName, int dwDesiredAccess, int dwShareMode,
                      Pointer lpSecurityAttributes, int dwCreationDisposition,
                      int dwFlagsAndAttributes, HANDLE hTemplateFile);

    boolean DeviceIoControl(HANDLE hDevice, int dwIoControlCode,
                            Pointer lpInBuffer, int nInBufferSize,
                            Pointer lpOutBuffer, int nOutBufferSize,
                            IntByReference lpBytesReturned, Pointer lpOverlapped);

    boolean CloseHandle(HANDLE hObject);
}

class WinConst {
    public static final int GENERIC_READ  = 0x80000000;
    public static final int FILE_SHARE_READ  = 0x00000001;
    public static final int FILE_SHARE_WRITE = 0x00000002;
    public static final int OPEN_EXISTING = 3;
    public static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;

    public static final int FSCTL_ENUM_USN_DATA     = 0x000900b3;
    public static final int FSCTL_READ_USN_JOURNAL  = 0x000900f3;

    public static final int FILE_ATTRIBUTE_DIRECTORY = 0x00000010;
}
