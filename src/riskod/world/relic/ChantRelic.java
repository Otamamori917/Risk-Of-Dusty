package riskod.world.relic;

import riskod.world.run.RunState;

/** Relic whose bonus values are multiplied by 1.25 per shrine counted when this copy was given by a chant. */
public class ChantRelic extends RelicType {
    public ChantRelic(String name) {
        super(name);
    }

    /** Passive copies carry their own shrine count; an equipped relic is a single copy, so it uses the count recorded for its type. */
    public float scale(int shrines) {
        if (slotKind == SlotKind.passive) return shrines * RunState.CHANT_SCALE_PER_SHRINE;
        return RunState.chantScale(name);
    }
}