package com.potatorecruits;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.api.equipment.potatoCannon.PotatoCannonProjectileType;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Lets any Recruits unit that holds a Create Potato Cannon (and has ammo in its inventory) shoot at its target.
 * Mirrors what {@link PotatoCannonItem#use} does for a player.
 */
public class PotatoCannonGoal extends Goal {
    private final AbstractRecruitEntity recruit;
    private int cooldown = 0;
    private int repath = 0;

    public PotatoCannonGoal(AbstractRecruitEntity recruit) {
        this.recruit = recruit;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    // ------------------------------------------------------------------ ammo / weapon

    private static boolean isCannon(ItemStack s) {
        return s.getItem() instanceof PotatoCannonItem;
    }

    private Optional<PotatoCannonProjectileType> typeOf(ItemStack s) {
        if (s.isEmpty()) {
            return Optional.empty();
        }
        return PotatoCannonProjectileType.getTypeForItem(recruit.level().registryAccess(), s.getItem())
                .map(Holder.Reference::value);
    }

    private int findAmmoSlot() {
        var inv = recruit.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && !isCannon(s) && typeOf(s).isPresent()) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasCannon() {
        if (isCannon(recruit.getMainHandItem())) {
            return true;
        }
        // cannon is in the backpack: take it into the hand
        recruit.switchMainHandItem(PotatoCannonGoal::isCannon);
        return isCannon(recruit.getMainHandItem());
    }

    // ------------------------------------------------------------------ goal

    @Override
    public boolean canUse() {
        LivingEntity target = recruit.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (recruit.getState() == 3 || recruit.needsToGetFood()) {
            return false;
        }
        if (!hasCannon()) {
            return false;
        }
        return !PotatoConfigHelper.needsAmmo() || findAmmoSlot() >= 0;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        recruit.setAggressive(true);
        cooldown = Math.max(cooldown, 10);
    }

    @Override
    public void stop() {
        recruit.setAggressive(false);
        recruit.getNavigation().stop();
        repath = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = recruit.getTarget();
        if (target == null) {
            return;
        }
        recruit.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double range = PotatoConfig.RANGE.get();
        double dist = recruit.distanceTo(target);
        boolean canSee = recruit.getSensing().hasLineOfSight(target);
        boolean inRange = dist <= range && canSee;

        // movement
        boolean hold = recruit.getShouldHoldPos();
        Entity owner = recruit.getOwner();
        boolean followOwnerClose = recruit.getShouldFollow() && owner != null && owner.distanceToSqr(recruit) <= 100.0D;
        if (inRange || hold || followOwnerClose) {
            recruit.getNavigation().stop();
        } else if (--repath <= 0) {
            repath = 10;
            recruit.getNavigation().moveTo(target, 1.0D);
        }

        if (cooldown > 0) {
            cooldown--;
        }
        if (inRange && cooldown <= 0) {
            int next = shoot(target);
            if (next > 0) {
                cooldown = next;
            }
        }
    }

    // ------------------------------------------------------------------ shooting

    /** @return ticks until the next shot, or 0 if nothing was fired */
    private int shoot(LivingEntity target) {
        if (!(recruit.level() instanceof ServerLevel level)) {
            return 0;
        }
        ItemStack cannon = recruit.getMainHandItem();
        if (!isCannon(cannon)) {
            return 0;
        }
        boolean consume = PotatoConfig.CONSUME_AMMO.get();
        int slot = findAmmoSlot();
        if (slot < 0) {
            return 0;
        }
        ItemStack ammo = recruit.getInventory().getItem(slot);
        Optional<PotatoCannonProjectileType> opt = typeOf(ammo);
        if (opt.isEmpty()) {
            return 0;
        }
        PotatoCannonProjectileType type = opt.get();

        Vec3 eye = recruit.getEyePosition();
        Vec3 aimPoint = target.getEyePosition().subtract(0.0D, target.getBbHeight() * 0.15D, 0.0D);
        double dist = aimPoint.subtract(eye).length();
        double speed = 2.0D * type.velocityMultiplier();
        // gravity compensation (the projectile falls a bit over longer distances)
        double flight = dist / Math.max(0.1D, speed);
        double lift = 0.02D * type.gravityMultiplier() * flight * flight;
        Vec3 dir = aimPoint.add(0.0D, lift, 0.0D).subtract(eye).normalize();

        Vec3 barrel = eye.add(dir.scale(1.0D)).subtract(0.0D, 0.15D, 0.0D);

        if (PotatoConfig.AVOID_FRIENDLY_FIRE.get() && friendlyInLine(barrel, aimPoint, target)) {
            return 4; // check again very soon
        }

        Vec3 motion = dir.scale(speed);
        int split = Math.max(1, type.split());
        float pitch = type.soundPitch() + (level.random.nextFloat() - 0.5F) / 4.0F;
        double spread = 0.012D * Mth.clamp(dist / 8.0D, 0.5D, 4.0D);

        ItemStack ammoCopy = ammo.copy();
        ammoCopy.setCount(1);
        for (int i = 0; i < split; i++) {
            PotatoProjectileEntity projectile = (PotatoProjectileEntity) AllEntityTypes.POTATO_PROJECTILE.create(level);
            if (projectile == null) {
                return 0;
            }
            projectile.setItem(ammoCopy);
            projectile.setEnchantmentEffectsFromCannon(cannon);
            Vec3 m = motion;
            double s = split > 1 ? spread * 6.0D : spread;
            m = m.add((level.random.nextDouble() - 0.5D) * s, (level.random.nextDouble() - 0.5D) * s,
                    (level.random.nextDouble() - 0.5D) * s);
            projectile.setPos(barrel.x, barrel.y, barrel.z);
            projectile.setDeltaMovement(m);
            projectile.setOwner(recruit);
            level.addFreshEntity(projectile);
        }
        PotatoProjectileEntity.playLaunchSound(level, barrel, pitch);
        recruit.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        if (consume) {
            ammo.shrink(1);
            if (ammo.isEmpty()) {
                recruit.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            recruit.onInventoryChanged();
        }
        if (PotatoConfig.DAMAGE_CANNON.get()) {
            cannon.hurtAndBreak(1, recruit, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        return Math.max(PotatoConfig.MIN_INTERVAL_TICKS.get(), type.reloadTicks());
    }

    private boolean friendlyInLine(Vec3 from, Vec3 to, LivingEntity target) {
        AABB box = new AABB(from, to).inflate(1.0D);
        java.util.UUID ownerId = recruit.getOwnerUUID();
        for (LivingEntity e : recruit.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == recruit || e == target) {
                continue;
            }
            boolean friend = (ownerId != null && e.getUUID().equals(ownerId))
                    || (e instanceof AbstractRecruitEntity r && ownerId != null && ownerId.equals(r.getOwnerUUID()));
            if (friend && e.getBoundingBox().inflate(0.3D).clip(from, to).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
