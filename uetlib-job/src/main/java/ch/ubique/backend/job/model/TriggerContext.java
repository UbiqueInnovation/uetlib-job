package ch.ubique.backend.job.model;

public sealed interface TriggerContext permits EmptyTriggerContext, FileTriggerContext {}
