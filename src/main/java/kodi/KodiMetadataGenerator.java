package kodi;

import kodi.anime_details.AnimeDetailsLoader;
import kodi.anime_details.model.Anime;
import kodi.anime_mapping.AnimeMappingLoader;
import kodi.anime_mapping.model.AnimeMapping;
import kodi.nfo.*;
import kodi.tmdb.MovieData;
import kodi.tmdb.TmDbApi;
import kodi.tvdb.TVSeriesData;
import kodi.tvdb.TvDbApi;
import lombok.*;
import lombok.extern.java.Log;
import processing.FileInfo;
import processing.tagsystem.TagSystemTags;
import utils.http.DownloadHelper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Log
@RequiredArgsConstructor
public class KodiMetadataGenerator {

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Map<Long, AnimeMapping> animeMapping = initAnimeMapping();
    private final Map<Long, SeriesNfoWriter> nfoGenerators = new HashMap<>();
    private final DownloadHelper downloadHelper;
    private final TvDbApi tvDbApi;
    private final TmDbApi tmDbApi;
    private final String animeMappingUrl;
    private final EnumSet<OverwriteConfiguration> overwriteConfiguration;

    private Map<Long, AnimeMapping> initAnimeMapping() {
        return new AnimeMappingLoader(animeMappingUrl).getAnimeMapping();
    }

    public void generateMetadata(FileInfo fileInfo, OnDone onDone) {
        log.info(STR."Generating metadata for \{fileInfo.getFile().getName()}");
        val aniDbAnimeId = Integer.parseInt(fileInfo.getData().get(TagSystemTags.AnimeId));
        var anime = AnimeDetailsLoader.parseXml(getXmlInput(aniDbAnimeId));
        log.info(STR."Anime: \{anime.getId()} - \{anime.getTitles().stream().findFirst()}");
        if (anime.getType() == Anime.Type.TV_Series) {
            val tvDbId = this.getAnimeMapping().get((long) aniDbAnimeId).getTvDbId();
            tvDbApi.getTvSeriesData(tvDbId, tvDbAllData -> {
                writeSeriesMetadata(Optional.ofNullable(tvDbAllData), fileInfo, anime, aniDbAnimeId, onDone);
            });
        } else if (anime.getType() == Anime.Type.MOVIE) {
            val tmDbIds = this.getAnimeMapping().get((long) aniDbAnimeId).getTmDbIds();
            if (!tmDbIds.isEmpty()) {
                tmDbApi.getMovieInfo(Integer.parseInt(tmDbIds.get(0)), movieData -> {
                    writeMovieMetadata(Optional.ofNullable(movieData), fileInfo, anime, onDone);
                });
            }
        } else {
            writeSeriesMetadata(Optional.empty(), fileInfo, anime, aniDbAnimeId, onDone);
        }
    }

    private void writeMovieMetadata(Optional<MovieData> movieData, FileInfo fileInfo, Anime anime, OnDone onDone) {
        val movieBuilder = fileInfo.toMovie();
        anime.updateMovie(movieBuilder);
        movieData.ifPresent(data -> {
            log.info(STR."TMDB: \{data.getId()} - \{data.getTitle()}");
            data.updateMovie(movieBuilder);
        });
        var movie = movieBuilder.build();
        val generator = MovieNfoWriter.forMovie(movie);
        generator.writeNfoFile(overwriteConfiguration.contains(OverwriteConfiguration.OVERWRITE_MOVIES));
        exportImages(movie);
        onDone.onDone();
    }

    private void writeSeriesMetadata(Optional<TVSeriesData> tvDbAllData, FileInfo fileInfo, Anime anime, int aniDbAnimeId, OnDone onDone) {
        val series = anime.toSeries();
        tvDbAllData.ifPresent(tvDbData -> {
            log.info(STR."TVDB: \{tvDbData.getSeriesId()} - \{tvDbData.getSeriesName()}");
            tvDbData.updateSeries(series);
        });
        SeriesNfoWriter generator;
        if (nfoGenerators.containsKey((long) aniDbAnimeId)) {
            generator = nfoGenerators.get((long) aniDbAnimeId);
        } else {
            generator = SeriesNfoWriter.forSeries(series.build());
            nfoGenerators.put((long) aniDbAnimeId, generator);
        }

        val episodeFileData = fileInfo.toAniDBFileData();
        val filePath = fileInfo.getFinalFilePath();
        val episode = episodeFileData.toEpisode();
        episode.filePath(filePath);
        anime.updateEpisode(episode, episodeFileData.aniDbEpisodeId());
        tvDbAllData.ifPresent(tvDbData -> {
            tvDbData.updateEpisode(episode, episodeFileData.seasonNumber(), episodeFileData.episodeNumber());
        });

        val watchDate = fileInfo.getWatchedDate();
        if (watchDate != null) {
            episode.lastPlayed(fileInfo.getWatchedDate().toLocalDate());
        }

        val episodeData = episode.build();
        generator.writeNfoFiles(episodeData, overwriteConfiguration.contains(OverwriteConfiguration.OVERWRITE_SERIES),
                overwriteConfiguration.contains(OverwriteConfiguration.OVERWRITE_EPISODE));
        exportImages(generator.getSeries(), episodeData);
        onDone.onDone();
    }

    private void exportImages(Series series, Episode episode) {
        val seriesFolder = episode.getFilePath().getParent();
        writeFile(episode.getThumbnail(), seriesFolder.resolve(STR."\{episode.getFileNameWithoutExtension()}-thumb.jpg"));
        for (var i = 0; i < series.getFanarts().size(); i++) {
            val url = series.getFanarts().get(i).getUrl();
            val extension = url.substring(url.lastIndexOf("."));
            val name = i == 0 ? "fanart" : STR."fanart\{i}";
            writeFile(url, seriesFolder.resolve(STR."\{name}\{extension}"));
        }
        series.getArtworks().stream().filter(a -> a.getType() == Artwork.ArtworkType.SERIES_POSTER).findFirst().ifPresent(
                a -> writeFile(a.getUrl(), seriesFolder.resolve("poster.jpg"))
        );
        series.getArtworks().stream().filter(a -> a.getType() == Artwork.ArtworkType.SERIES_BANNER).findFirst().ifPresent(
                a -> writeFile(a.getUrl(), seriesFolder.resolve("banner.jpg"))
        );
        val actorPath = seriesFolder.resolve(".actors");
        series.getActors().forEach(actor -> {
            writeFile(actor.getThumb(), actorPath.resolve(STR."\{actor.getName().replaceAll(" ", "_")}.jpg"));
        });
    }

    private void exportImages(Movie movie) {
        val movieFolder = movie.getFilePath().getParent();
        writeFile(movie.getThumbnail(), movieFolder.resolve(STR."\{movie.getFileNameWithoutExtension()}-thumb.jpg"));
        for (var i = 0; i < Math.max(Math.min(movie.getFanarts().size(), 15), 15); i++) {
            val url = movie.getFanarts().get(i).getUrl();
            val extension = url.substring(url.lastIndexOf("."));
            val name = i == 0 ? "fanart" : STR."fanart\{i}";
            writeFile(url, movieFolder.resolve(STR."\{name}\{extension}"));
        }
        val actorPath = movieFolder.resolve(".actors");
        movie.getActors().forEach(actor -> {
            writeFile(actor.getThumb(), actorPath.resolve(STR."\{actor.getName().replaceAll(" ", "_")}.jpg"));
        });
    }

    private void writeFile(String url, Path path) {
        if (Files.exists(path) || !overwriteConfiguration.contains(OverwriteConfiguration.OVERWRITE_ARTWORK)) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            downloadHelper.downloadToFile(url, path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream getXmlInput(int aniDbAnimeId) {
        val path = Path.of(STR."\{aniDbAnimeId}.xml");
        try {
            if (!Files.exists(path)) {
                downloadHelper.downloadToFile(AnimeDetailsLoader.getAnidbDetailsXmlUrl(aniDbAnimeId), path);
            }
            return new FileInputStream(path.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public interface OnDone {
        void onDone();
    }
}
