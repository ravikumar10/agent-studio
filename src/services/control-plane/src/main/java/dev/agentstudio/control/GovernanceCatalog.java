package dev.agentstudio.control;

import dev.agentstudio.domain.Capability;
import dev.agentstudio.domain.ModelProfile;
import java.util.List;

public interface GovernanceCatalog {
    Capability createCapability(Capability capability);
    List<Capability> capabilities(String tenantId);
    ModelProfile createModelProfile(ModelProfile profile);
    List<ModelProfile> modelProfiles(String tenantId);
}
