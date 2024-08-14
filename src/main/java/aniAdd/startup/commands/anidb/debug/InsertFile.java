package aniAdd.startup.commands.anidb.debug;

import aniAdd.startup.commands.debug.InsertResponse;
import cache.AniDBFileRepository;
import cache.PersistenceConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import processing.FileInfo;
import processing.tagsystem.TagSystemTags;
import udpapi.ParseReply;
import udpapi.command.FileCommand;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

@Slf4j
@CommandLine.Command(name = "insert-file",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Insert Reply from message")
public class InsertFile implements Callable<Integer> {
    @CommandLine.Option(names = "--ed2k", description = "The ed2k hash to use", required = false, defaultValue = "057ec5dba1ea40c1e82e24a0f70405d5")
    private String ed2k;

    @CommandLine.Option(names = "--size", description = "The file size to use", required = false, defaultValue = "524504029")
    private int fileSize;

    @CommandLine.Option(names = "--filepath", description = "The file path to use", required = false, defaultValue = "Z:\\2-WorkingImport\\tvdb\\Nura - Rise of the Yokai Clan [tvdb-171731]\\Nura - Rise of the Yokai Clan - S00E06 - Zero Tears Snow.mkv")
    private String filePath;

    @CommandLine.ParentCommand
    DebugCommand parent;

    @Override
    public Integer call() throws Exception {
        try (val executorService = Executors.newScheduledThreadPool(10)) {
            val udpApi = parent.getUdpApi(parent.getConfiguration(), executorService);
            udpApi.registerCallback(FileCommand.class, data -> {
                if (!data.getReply().getReplyStatus().success()) {
                    log.error(STR."Cannot insert data for non successful file command: \{data.getReply()}");
                }
                try (val sessionFactory = PersistenceConfiguration.getSessionFactory(parent.getDbPath())) {
                    val repository = new AniDBFileRepository(sessionFactory);
                    val info = new FileInfo(new FakeFile(Path.of(filePath), fileSize, true), 1);
                    info.getData().put(TagSystemTags.Ed2kHash, ed2k);
                    FileCommand.AddReplyToDict(info.getData(), data.getReply(), false);
                    repository.saveAniDBFileData(info.toAniDBFileData());
                    log.info(STR."Inserted file data for \{info.getFile().getName()}");
                }
            });

            udpApi.queueCommand(FileCommand.Create(1, fileSize, ed2k));
            val _ = executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        }
        return 0;
    }

    private class FakeFile extends File {
        private final Path path;
        private final int size;
        private final FakeFile parent;

        public FakeFile(@NotNull Path path, int size, boolean setUpParent) {
            super(path.toString());
            this.path = path;
            this.size = size;
            if (setUpParent) {
                parent = new FakeFile(path.getParent(), 0, false);
            } else {
                parent = null;
            }
        }

        @Override
        public File getParentFile() {
            return parent;
        }

        @NotNull
        @Override
        public String getName() {
            return path.getFileName().toString();
        }

        @Override
        public long length() {
            return size;
        }
    }
}
