package kodi.tmdb;

import kodi.nfo.Artwork;
import kodi.nfo.Movie;
import kodi.nfo.Rating;
import lombok.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Value
@Builder()
public class MovieData {
    private final static String artUrl = "https://image.tmdb.org/t/p/original";

    int id;
    String title;
    String originalTitle;
    String plot;
    int voteCount;
    double voteAverage;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    Optional<TmDbMovieVideosResponse.Video> trailer;
    List<TmDbMovieImagesResponse.Image> backdrops;
    List<TmDbMovieImagesResponse.Image> posters;

    public void updateMovie(Movie.MovieBuilder builder) {
        trailer.ifPresent(trailer -> builder.trailer(STR."https://www.youtube.com/watch?v=\{trailer.getKey()}"));
        builder.title(title)
                .originalTitle(title)
                .plot(plot)
                .rating(Rating.builder().name("tmdb").max(10).rating(voteAverage).voteCount(voteCount).build());

        backdrops.stream().sorted(Comparator.comparingDouble(TmDbMovieImagesResponse.Image::getVoteAverage)
                        .thenComparingInt(TmDbMovieImagesResponse.Image::getVoteCount))
                .limit(40)
                .forEach(backdrop -> builder.fanart(Artwork.builder()
                        .url(STR."\{artUrl}\{backdrop.getFilePath()}")
                        .type(Artwork.ArtworkType.MOVIE_BACKGROUND)
                        .build()));

        posters.stream().sorted(Comparator.comparingDouble(TmDbMovieImagesResponse.Image::getVoteAverage)
                        .thenComparingInt(TmDbMovieImagesResponse.Image::getVoteCount))
                .limit(10)
                .forEach(poster -> builder.fanart(Artwork.builder()
                        .url(STR."\{artUrl}\{poster.getFilePath()}")
                        .type(Artwork.ArtworkType.MOVIE_POSTER)
                        .build()));

    }

    public static class MovieDataBuilder {
        private boolean trailersFailed = false;
        private boolean imageFailed = false;
        private boolean detailsFailed = false;

        public boolean isComplete() {
            return trailersFinished() && imagesFinished() && detailsFinished();
        }

        private boolean imagesFinished() {
            return this.posters != null || this.imageFailed;
        }

        private boolean trailersFinished() {
            //noinspection OptionalAssignedToNull
            return this.trailer != null || this.trailersFailed;
        }

        private boolean detailsFinished() {
            return this.plot != null || this.detailsFailed;
        }

        public MovieDataBuilder videos(TmDbMovieVideosResponse videos) {
            if (videos == null) {
                this.trailersFailed = true;
                this.trailer = Optional.empty();
                return this;
            }
            this.trailer = videos.getEnglishTrailer();
            return this;
        }

        public MovieDataBuilder images(TmDbMovieImagesResponse images) {
            if (images == null) {
                this.imageFailed = true;
                this.posters(List.of());
                this.backdrops(List.of());
                return this;
            }
            this.posters(images.getPosters());
            this.backdrops(images.getBackdrops());
            return this;
        }

        public MovieDataBuilder details(TmDbMovieDetailsResponse details) {
            if (details == null) {
                this.detailsFailed = true;
                return this;
            }
            return this.id(details.getId())
                    .title(details.getTitle())
                    .originalTitle(details.getOriginalTitle())
                    .plot(details.getPlot())
                    .voteCount(details.getVoteCount())
                    .voteAverage(details.getVoteAverage());
        }
    }

}
