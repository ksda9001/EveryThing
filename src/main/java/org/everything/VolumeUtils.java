package org.everything;
import java.io.File;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class VolumeUtils {

    /**
     * 列出所有 NTFS 卷（盘符）
     */
    public static List<String> listNtfsVolumes() {
        List<String> vols = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots == null) return vols;

        for (File root : roots) {
            try {
                FileStore store = Files.getFileStore(Paths.get(root.getAbsolutePath()));
                String type = store.type();
                if ("NTFS".equalsIgnoreCase(type)) {
                    // 返回 "C:" 这种格式
                    String path = root.getAbsolutePath();
                    if (path.endsWith("\\")) path = path.substring(0, path.length() - 1);
                    vols.add(path);
                }
            } catch (Exception ignored) {
                // 某些卷可能无法访问，忽略
            }
        }
        return vols;
    }

    // 简单测试
    public static void main(String[] args) {
        System.out.println("检测到 NTFS 卷: " + listNtfsVolumes());
    }
}
