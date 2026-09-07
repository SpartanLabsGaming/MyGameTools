package com.spartanlabs.gaming.networking.command

//region 2. Intended Function
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
//endregion

/**
 * The [SerializersModule] that registers GameTools' six standard [ClientCommand]s for
 * polymorphic encoding and decoding, keyed by their `@SerialName`s (`gametools.*`).
 *
 * [ClientCommandCodec] always folds this in; it is exposed so a consumer who builds their own
 * [Json] instead of using the codec can `+` it onto their own module.
 */
val StandardClientCommands: SerializersModule = SerializersModule {
    polymorphic(ClientCommand::class) {
        subclass(MoveTo::class)
        subclass(MoveDir::class)
        subclass(Follow::class)
        subclass(Stop::class)
        subclass(Attack::class)
        subclass(StopAttack::class)
    }
}

/**
 * Encodes and decodes [ClientCommand]s for the wire.
 *
 * ### Envelope
 *
 * A command datagram is the verb [COMMAND_VERB], a single space, then the command as a
 * polymorphic JSON object carrying a `type` discriminator - the same shape STATE and INPUT
 * use (`STATE <json>`, `INPUT <json>`). [encode] produces the whole `COMMAND <json>` string a
 * client sends; [decode] takes the part *after* the verb (which is what
 * [com.spartanlabs.gaming.networking.GameServer] hands it) and returns the command or a
 * [Result.failure] if the payload will not parse.
 *
 * ### The "same module both ends" contract
 *
 * The library's own commands always round-trip. A **consumer** command only round-trips if
 * the client's codec and the server's codec were both built with a module registering it (see
 * [ClientCommand]). Build one [ClientCommandCodec] per protocol version and share it between
 * the two sides.
 *
 * @param appCommands polymorphic registrations for the consumer's own [ClientCommand] types,
 *   e.g. `SerializersModule { polymorphic(ClientCommand::class) { subclass(BuildStructure::class) } }`.
 *   Omit it to speak the standard set alone.
 */
class ClientCommandCodec(appCommands: SerializersModule = EmptySerializersModule()) {

    /**
     * The configured JSON reader/writer. Separate from
     * [com.spartanlabs.gaming.networking.GameServer]'s default `Json` (which handles STATE and
     * INPUT): this one carries the polymorphic [ClientCommand] module and a `type`
     * discriminator matching `DrawableSnapshot`'s convention. Do not collapse the two - the
     * module wiring is what makes consumer commands decodable.
     */
    private val json: Json = Json {
        serializersModule = StandardClientCommands + appCommands
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    /** The full `COMMAND <json>` datagram text a client sends to deliver [command]. */
    fun encode(command: ClientCommand): String =
        "$COMMAND_VERB " + json.encodeToString(serializer, command)

    /**
     * Decodes the JSON body of a `COMMAND` datagram - the text after the [COMMAND_VERB] token.
     *
     * @param payload the command's JSON object as text
     * @return the decoded command, or [Result.failure] if [payload] is not a registered,
     *   well-formed [ClientCommand]
     */
    fun decode(payload: String): Result<ClientCommand> =
        runCatching { json.decodeFromString(serializer, payload) }

    companion object {
        /** The verb that opens a client command datagram, alongside `STATE` and `INPUT`. */
        const val COMMAND_VERB: String = "COMMAND"

        /** Resolves each concrete command through the polymorphic [ClientCommand] registrations. */
        private val serializer = PolymorphicSerializer(ClientCommand::class)
    }
}
