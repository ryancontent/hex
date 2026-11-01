package ly.anoma.hex.android;

/** Keeps processing pending long enough to interact before any result can commit. */
public final class SlowFixtureRecognitionService extends FixtureRecognitionService {
    @Override protected long resultDelayMillis() { return 4_000; }
}
