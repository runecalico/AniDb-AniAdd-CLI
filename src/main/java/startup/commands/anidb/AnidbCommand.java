package startup.commands.anidb;

import aniAdd.AniAdd;
import aniAdd.IAniAdd;
import cache.AniDBFileRepository;
import config.CliConfiguration;
import fileprocessor.DeleteEmptyChildDirectoriesRecursively;
import fileprocessor.FileProcessor;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import picocli.CommandLine;
import processing.DoOnFileSystem;
import processing.EpisodeProcessing;
import processing.FileHandler;
import startup.commands.CliCommand;
import startup.commands.anidb.debug.DebugCommand;
import startup.commands.util.CommandHelper;
import startup.validation.config.ConfigMustBeNull;
import startup.validation.validators.min.Min;
import startup.validation.validators.nonblank.NonBlank;
import startup.validation.validators.port.Port;
import udpapi.UdpApi;
import udpapi.reply.ReplyStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@CommandLine.Command(
        subcommands = {ScanCommand.class, KodiWatcherCommand.class, WatchCommand.class, WatchAndKodiCommand.class, DebugCommand.class},
        name = "anidb",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "AniDb handling")
public class AnidbCommand {
    @CommandLine.Option(names = {"-u", "--username"}, description = "The AniDB username", required = false, scope = CommandLine.ScopeType.INHERIT)
    @NonBlank(allowNull = true) String username;

    @ConfigMustBeNull(configPath = "anidb.password", envVariableName = "ANIDB_PASSWORD")
    @CommandLine.Option(names = {"-p", "--password"}, description = "The AniDB password", required = true, scope = CommandLine.ScopeType.INHERIT)
    @NonBlank String password;

    @CommandLine.Option(names = {"--localport"}, description = "The local port to use to connect to anidb", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "3333")
    @Port int localPort;

    @CommandLine.Option(names = {"--max-retries"}, description = "Maximum retries. NOT SUPPORTED YET", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "3")
    @Min(1) long maxRetries;

    @CommandLine.Option(names = {"--exit-on-ban"}, description = "Exit the application if the user is banned", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "false")
    boolean exitOnBan;

    @Getter
    @NonBlank
    @CommandLine.Option(names = {"--db"}, description = "The path to the sqlite db", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "aniAdd.sqlite")
    Path dbPath;

    @CommandLine.ParentCommand
    private CliCommand parent;

    public UdpApi getUdpApi(CliConfiguration configuration, ScheduledExecutorService executorService) {
        val username = this.username == null ? configuration.anidb().username() : this.username;
        if (username == null) {
            log.error("No username provided. Please provide a username in the config file or as a parameter.");
            return null;
        }

        return new UdpApi(executorService, localPort,username, password, configuration);
    }

    public Optional<IAniAdd> initializeAniAdd(boolean terminateOnCompletion, ScheduledExecutorService
            executorService, DoOnFileSystem fileSystem, Path inputDirectory, SessionFactory sessionFactory) {
        val config = parent.getConfiguration();
        if (config == null) {
            log.error(STR."No configuration loaded. Check the path to the config file. \{parent.getConfigPath()}");
            return Optional.empty();
        }

        val udpApi = getUdpApi(config, executorService);
        val fileHandler = new FileHandler();
        val fileRepository = new AniDBFileRepository(sessionFactory);
        val processing = new EpisodeProcessing(config, udpApi, fileSystem, fileHandler, fileRepository);
        val fileProcessor = new FileProcessor(processing, config, executorService);

        if (config.move().deleteEmptyDirs() && inputDirectory != null) {
            processing.addListener(event -> {
                if (event == EpisodeProcessing.ProcessingEvent.Done) {
                    fileSystem.run(new DeleteEmptyChildDirectoriesRecursively(inputDirectory));
                }
            });
        }

        val aniAdd = new AniAdd(config, udpApi, terminateOnCompletion, fileProcessor, processing, _ -> {
            log.info("Shutdown complete");
            executorService.shutdownNow();
        });
        if (exitOnBan) {
            udpApi.registerCallback(ReplyStatus.BANNED, _ -> {
                log.error("User is banned. Exiting.");
                aniAdd.Stop();
                // Make sure we shut down even if terminateOnCompletion is false
                if (!executorService.isShutdown()) {
                    executorService.shutdownNow();
                }
            });
        }

        return Optional.of(aniAdd);
    }

    public static List<String> getOptions() {
        return CommandHelper.getOptions(AnidbCommand.class, Set.of("password"));
    }

    public static String getName() {
        return CommandHelper.getName(AnidbCommand.class);
    }

    public CliConfiguration getConfiguration() {
        return parent.getConfiguration();
    }
}
