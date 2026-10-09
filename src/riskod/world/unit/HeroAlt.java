package riskod.world.unit;

import arc.struct.Seq;
import riskod.world.meta.UnlockReq;
import riskod.world.relic.RelicType;

/** One alternate ability/gear option for a hero slot. */
public class HeroAlt {
    public RelicType relic;
    public UnlockReq unlock = UnlockReq.none();

    public HeroAlt(RelicType relic) {
        this.relic = relic;
    }

    public HeroAlt(RelicType relic, UnlockReq unlock) {
        this.relic = relic;
        this.unlock = unlock != null ? unlock : UnlockReq.none();
    }

    public boolean unlocked() {
        return unlock == null || unlock.met();
    }
}