package aniAdd.startup.commands.anidb;

import aniAdd.startup.validation.validators.nonempty.NonEmpty;
import lombok.val;
import picocli.CommandLine;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@CommandLine.Command(name = "scan", mixinStandardHelpOptions = true, version = "1.0",
        description = "Scans the directory for files and adds them to AniDb")
public class ScanCommand implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "The directory to scan.")
    @NonEmpty
    private String directory;

    @CommandLine.ParentCommand
    private AnidbCommand parent;

    @Override
    public Integer call() throws Exception {
        try (val executorService = Executors.newScheduledThreadPool(10)) {
            val aniAdd = parent.initializeAniAdd(true, executorService);
            aniAdd.ProcessDirectory(directory);

            val _ = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        return 0;
    }
}
