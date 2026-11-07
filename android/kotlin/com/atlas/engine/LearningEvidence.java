package com.atlas.engine;

enum LearningEvidence {
    CHOICE(false), CORRECTION(true), REPLACEMENT(true), KEPT(false);

    final boolean replacement;
    LearningEvidence(boolean replacement) { this.replacement = replacement; }
}
