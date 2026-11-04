package com.example.discordautojoin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.List;
import java.util.Random;

/** Client-side only: plays chaotic noteblock sounds that only you can hear. */
public final class NoteNoises {
    private static final Random RANDOM = new Random();
    private static final int DURATION_TICKS = 160;
    private static int ticksLeft = 0;

    private static final List<SoundEvent> INSTRUMENTS = List.of(
            SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_GUITAR.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_BANJO.value(),
            SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()
    );

    private NoteNoises() {
    }

    public static void start() {
        ticksLeft = DURATION_TICKS;
    }

    public static void stop() {
        ticksLeft = 0;
    }

    public static void tick(MinecraftClient client) {
        if (ticksLeft <= 0) return;
        ticksLeft--;
        if (client.world == null) {
            ticksLeft = 0;
            return;
        }
        // Burst of 1-2 random notes every 2 ticks, with a mix of musical and totally off-key pitches.
        if (ticksLeft % 2 == 0) {
            int notes = 1 + RANDOM.nextInt(2);
            for (int i = 0; i < notes; i++) {
                SoundEvent sound = INSTRUMENTS.get(RANDOM.nextInt(INSTRUMENTS.size()));
                float pitch = RANDOM.nextBoolean()
                        ? (float) Math.pow(2.0, (RANDOM.nextInt(25) - 12) / 12.0) // proper noteblock pitch
                        : 0.5f + RANDOM.nextFloat() * 1.5f;                        // weird in-between pitch
                client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, 1.0f));
            }
        }
    }
}
