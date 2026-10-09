package com.lewandivka.core.dialogue;

/**
 * A button offered after the last line of a script. The id is sent back by the client and
 * validated on the server against the choices of the running script.
 */
public record DialogueChoice(String id, String textKey) {
}
