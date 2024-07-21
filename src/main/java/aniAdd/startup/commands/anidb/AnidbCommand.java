package aniAdd.startup.commands.anidb;

import aniAdd.AniAdd;
import aniAdd.IAniAdd;
import aniAdd.config.AniConfiguration;
import aniAdd.startup.commands.CliCommand;
import aniAdd.startup.validation.validators.min.Min;
import aniAdd.startup.validation.validators.nonempty.NonEmpty;
import aniAdd.startup.validation.validators.port.Port;
import fileprocessor.FileProcessor;
import lombok.extern.java.Log;
import lombok.val;
import picocli.CommandLine;
import processing.FileHandler;
import processing.Mod_EpProcessing;
import udpapi2.UdpApi;
import udpapi2.reply.ReplyStatus;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

@Log
@CommandLine.Command(
        subcommands = {ScanCommand.class, KodiWatcherCommand.class, WatchCommand.class},
        name = "anidb",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "AniDb handling")
public class AnidbCommand {
    @CommandLine.Option(names = {"-u", "--username"}, description = "The AniDB username", required = true, scope = CommandLine.ScopeType.INHERIT)
    @NonEmpty String username;

    @CommandLine.Option(names = {"-p", "--password"}, description = "The AniDB password", required = true, scope = CommandLine.ScopeType.INHERIT)
    @NonEmpty String password;

    @CommandLine.Option(names = {"--localport"}, description = "The local port to use to connect to anidb", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "3333")
    @Port int localPort;

    @CommandLine.Option(names = {"--max-retries"}, description = "Maximum retriers", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "3")
    @Min(1) long maxRetries;

    @CommandLine.Option(names = {"--exit-on-ban"}, description = "Exit the application if the user is banned", required = false, scope = CommandLine.ScopeType.INHERIT, defaultValue = "false")
    boolean exitOnBan;

    @CommandLine.Option(names = {"-c", "--config"}, description = "The path to the config file. Specified parameters will override values from the config file.", required = true, scope = CommandLine.ScopeType.INHERIT)
    String configPath;

    @CommandLine.ParentCommand
    private CliCommand parent;

    Optional<AniConfiguration> getConfiguration() {
        return parent.getConfiguration(false, configPath);
    }

    Optional<AniConfiguration> getConfigurationOrDefault() {
        return parent.getConfiguration(true, configPath);
    }

    private UdpApi getUdpApi(AniConfiguration configuration, ScheduledExecutorService executorService) {
        val udpApi = new UdpApi(executorService, localPort, username, password);
        udpApi.Initialize(configuration);
        return udpApi;
    }

    public Optional<IAniAdd> initializeAniAdd(boolean terminateOnCompletion, ScheduledExecutorService executorService) {
        val configuration = getConfiguration();
        if (configuration.isEmpty()) {
            log.severe(STR."No configuration loaded. Check the path to the config file. \{configPath}");
            return Optional.empty();
        }

        val udpApi = getUdpApi(configuration.get(), executorService);

        val processing = new Mod_EpProcessing(configuration.get(), udpApi, executorService, new FileHandler());
        val fileProcessor = new FileProcessor(processing, configuration.get(), executorService);

        val aniAdd = new AniAdd(configuration.get(), udpApi, terminateOnCompletion, fileProcessor, processing, _ -> executorService.shutdownNow());
        if (exitOnBan) {
            udpApi.registerCallback(ReplyStatus.BANNED, _ -> {
                log.severe("User is banned. Exiting.");
                aniAdd.Stop();
                // Make sure we shut down even if terminateOnCompletion is false
                if (!executorService.isShutdown()) {
                    executorService.shutdownNow();
                }
            });
        }

        return Optional.of(aniAdd);
    }
}
