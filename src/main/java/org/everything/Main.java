package org.everything;

public class Main {
    public static IndexStore globalStore = new IndexStore();

    public static void main(String[] args) {
        NtfsUsnEnumerator enumr = new NtfsUsnEnumerator();

        for (String vol : VolumeUtils.listNtfsVolumes()) {
            new Thread(() -> {
                enumr.enumerateVolume(vol,
                        (frn, pfrn, name, isDir) -> globalStore.upsert(frn, pfrn, name, isDir, vol),
                        count -> globalStore.reportProgress(vol, count),
                        v -> globalStore.markComplete(v));
            }, "enum-" + vol).start();

            UsnTailer tailer = new UsnTailer(vol, globalStore);
            tailer.start();
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            new SearchFrame(globalStore).setVisible(true);
        });
    }
}
