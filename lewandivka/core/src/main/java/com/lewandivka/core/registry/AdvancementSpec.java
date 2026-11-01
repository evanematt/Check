package com.lewandivka.core.registry;

/**
 * One advancement of the Левандівка tab. Every advancement has a single criterion called {@code done} that the
 * server grants from code, so progress is always decided by the server.
 */
public record AdvancementSpec(String id, String parent, String icon, String frame, boolean hidden,
                              String titleUk, String titleEn, String descUk, String descEn) {
}
