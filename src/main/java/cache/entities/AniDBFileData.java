package cache.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import processing.tagsystem.TagSystemTags;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(AniDBFileData.AniDBFileId.class)
public class AniDBFileData {

    @Id
    @Column(nullable = false)
    @NonNull
    private String ed2k;

    @Id
    @Column(nullable = false)
    private long size;

    @Column(nullable = false, unique = true)
    @NonNull
    private String fileName;

    @Column(nullable = false)
    @NonNull
    private String folderName;

    @Singular
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(joinColumns = {
            @JoinColumn(name = "file_ed2k", nullable = false, referencedColumnName = "ed2k"),
            @JoinColumn(name = "file_size", nullable = false, referencedColumnName = "size")})

    private Map<TagSystemTags, String> tags;

    @CreationTimestamp
    @Column(updatable = false)
    @Setter(value = AccessLevel.NONE)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Setter(value = AccessLevel.NONE)
    private LocalDateTime updatedAt;


    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class AniDBFileId {
        private String ed2k;
        private long size;
    }

    public void update(AniDBFileData other) {
        if (!this.getEd2k().equals(other.getEd2k()) || this.getSize() != other.getSize()) {
            throw new IllegalArgumentException("Cannot update different files");
        }
        this.fileName = other.fileName;
        this.folderName = other.folderName;
        this.tags = other.tags;
    }
}
