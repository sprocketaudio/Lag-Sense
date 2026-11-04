package audio.sprocket.lagsense;

import java.util.Objects;

/** Safe GUI DTO. No Discord destination, credential or other server config is included. */
public record CaptureSettings(ProfileSchedule.Settings timer, SpikeDetector.Settings spikes) {
    public CaptureSettings { Objects.requireNonNull(timer, "Missing timer settings"); Objects.requireNonNull(spikes, "Missing spike settings"); }
}
