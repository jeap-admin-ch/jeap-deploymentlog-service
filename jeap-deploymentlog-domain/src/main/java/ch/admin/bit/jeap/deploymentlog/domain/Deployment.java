package ch.admin.bit.jeap.deploymentlog.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;
import java.util.*;

import static lombok.AccessLevel.PROTECTED;

@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Entity
@Table(uniqueConstraints = {@UniqueConstraint(name = "DEPLOYMENT_EXTERNAL_ID_UK", columnNames = {"externalId"})})
@Slf4j
public class Deployment {

    @Id
    private UUID id;

    @NonNull
    private String externalId;

    @NonNull
    private ZonedDateTime startedAt;

    private ZonedDateTime endedAt;

    @NonNull
    private ZonedDateTime lastModified;

    @Enumerated(EnumType.STRING)
    @NonNull
    private DeploymentState state;

    @Enumerated(EnumType.STRING)
    @NonNull
    private DeploymentSequence sequence;

    private String stateMessage;

    @NonNull
    private String startedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    @NonNull
    private Environment environment;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @NonNull
    private ComponentVersion componentVersion;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Changelog changelog;

    @ElementCollection(fetch = FetchType.LAZY)
    private Set<Link> links;

    private String remedyChangeId;

    @ElementCollection
    @CollectionTable(name = "deployment_properties", joinColumns = @JoinColumn(name = "deployment_id"))
    @MapKeyColumn(name = "property_name")
    @Column(name = "property_value")
    private Map<String, String> properties = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "deployment_references", joinColumns = @JoinColumn(name = "deployment_id"))
    @Column(name = "reference_identifier")
    @Enumerated(EnumType.STRING)
    private Set<String> referenceIdentifiers = new HashSet<>();

    @Embedded
    @AttributeOverride(name = "type", column = @Column(name = "target_type"))
    @AttributeOverride(name = "url", column = @Column(name = "target_url"))
    @AttributeOverride(name = "details", column = @Column(name = "target_details"))
    private DeploymentTarget target;

    @ElementCollection
    @CollectionTable(name = "deployment_types", joinColumns = @JoinColumn(name = "deployment_id"))
    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private Set<DeploymentType> deploymentTypes = new HashSet<>();

    @Builder
    @SuppressWarnings("java:S107")
    private Deployment(@NonNull String externalId,
                       @NonNull ZonedDateTime startedAt,
                       @NonNull String startedBy,
                       @NonNull Environment environment,
                       DeploymentTarget target,
                       @NonNull ComponentVersion componentVersion,
                       Set<Link> links,
                       Map<String, String> properties,
                       Set<String> referenceIdentifiers,
                       Changelog changelog,
                       @NonNull DeploymentSequence sequence,
                       String remedyChangeId,
                       Set<DeploymentType> deploymentTypes) {
        this.id = UUID.randomUUID();
        this.state = DeploymentState.STARTED;
        this.externalId = externalId;
        this.startedAt = startedAt;
        this.lastModified = ZonedDateTime.now();
        this.startedBy = startedBy;
        this.environment = environment;
        this.target = target;
        this.componentVersion = componentVersion;
        this.links = links;
        this.properties = new HashMap<>(properties == null ? Map.of() : properties);
        this.referenceIdentifiers = new HashSet<>(referenceIdentifiers == null ? Set.of() : referenceIdentifiers);
        this.changelog = changelog;
        this.sequence = sequence;
        this.remedyChangeId = remedyChangeId;
        this.deploymentTypes = new HashSet<>(deploymentTypes == null ? Set.of() : deploymentTypes);
    }

    public void failed(ZonedDateTime endedAt, String stateMessage) {
        this.endedAt = endedAt;
        this.lastModified = ZonedDateTime.now();
        this.state = DeploymentState.FAILURE;
        this.stateMessage = StringUtils.abbreviate(stateMessage, 1000);
    }

    public void success(ZonedDateTime endedAt, String stateMessage) {
        this.endedAt = endedAt;
        this.lastModified = ZonedDateTime.now();
        this.state = DeploymentState.SUCCESS;
        this.stateMessage = StringUtils.abbreviate(stateMessage, 1000);
    }

}
