package com.danyt.meerkat.core.nearby

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV

/** Facts an NFC tag gives about itself, named so the screen can label them. */
enum class NfcFact {
    Chip, ChipMaker, Applications, PaymentNetwork, CardName,
    Atqa, Sak, MifareClassic, MifareUltralight, MemoryBytes, Sectors, HistoricalBytes, HigherLayer,
    ApplicationData, ProtocolInfo, Manufacturer, SystemCode, Dsfid, MaxTransfer,
}

/** Things worth knowing about a tag, shown as notes beside its facts. */
enum class NfcNote {
    /** A bank card: many give out their number and expiry date to any reader that asks. */
    PaymentCard,

    /** A MIFARE Classic card: its data is locked with keys, which the app does not try. */
    LockedByKeys,

    /** A DESFire card that only lists its apps after signing in, which the app does not do. */
    AppsLocked,
}

/** The tag's NDEF area: its format, how much it holds, whether it can be written, and what is on it. */
data class NdefInfo(val type: String, val maxSize: Int, val writable: Boolean, val records: List<NdefContent>)

/**
 * One NFC tag that was held to the device.
 *
 * @property id the tag's identifier, in hex. Many cards make up a new random one for every read.
 * @property techs the technologies Android found, by their short names such as "NfcA" or "IsoDep".
 * @property readAt when it was read, in milliseconds since the epoch.
 */
data class NfcTag(
    val id: String,
    val techs: List<String>,
    val facts: List<Pair<NfcFact, String>>,
    val ndef: NdefInfo?,
    val readAt: Long,
    val notes: Set<NfcNote> = emptySet(),
    val card: CardDetails? = null,
    val cardProblem: String? = null,
)

/**
 * Reads NFC tags while a screen is in front, using Android's reader mode.
 *
 * Only what a tag tells every reader is used: its identifiers, its NDEF message (which Android reads when the
 * tag arrives), and the answers to a few identification commands that unlock nothing: the chip version of NXP
 * stickers and DESFire cards, the system information of ISO 15693 tags, and a bank card's payment directory,
 * which names its network. A bank card's number and expiry date are read only when the user turned that on, the
 * way a contactless terminal reads them; no payment is made. Nothing is written to a tag. While reader mode is
 * on, the device does not act as a card itself, so tap-to-pay pauses.
 */
class NfcReader(context: Context) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context.applicationContext)

    /** False on devices without NFC. */
    val isSupported: Boolean get() = adapter != null

    /** Whether NFC is switched on in the system settings. */
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    /**
     * Starts reading for [activity], which must be in front. [onTag] runs on a background thread.
     * [readCardDetails] is asked for each bank card: true reads its number and expiry date too.
     */
    fun start(activity: Activity, readCardDetails: () -> Boolean, onTag: (NfcTag) -> Unit) {
        adapter?.enableReaderMode(activity, { tag -> onTag(describe(tag, readCardDetails)) }, READER_FLAGS, null)
    }

    /** Stops reading for [activity]. Call it when the screen leaves the front. */
    fun stop(activity: Activity) {
        try {
            adapter?.disableReaderMode(activity)
        } catch (_: IllegalStateException) {
            // The activity is already gone; reader mode ended with it.
        }
    }

    private fun describe(tag: Tag, readCardDetails: () -> Boolean): NfcTag {
        val facts = mutableListOf<Pair<NfcFact, String>>()
        // Each technology is looked at separately, and one that misbehaves is skipped: tags and NFC chips
        // vary, and some devices throw for technologies they do not handle.
        fun read(block: () -> Unit) = try {
            block()
        } catch (_: RuntimeException) {
            // Leave this technology's facts out.
        } catch (_: java.io.IOException) {
            // The tag left the field or did not understand; leave this part out.
        }
        val notes = mutableSetOf<NfcNote>()
        var card: CardDetails? = null
        var cardProblem: String? = null
        identify(tag, facts, notes, ::read) { isoDep, applications ->
            if (readCardDetails()) {
                cardProblem = CardProblem.LEFT // Replaced below unless the card leaves the field first.
                val result = readCard(isoDep, applications)
                card = result.first
                cardProblem = result.second
            }
        }
        read {
            NfcA.get(tag)?.let {
                facts += NfcFact.Atqa to it.atqa.toHex()
                facts += NfcFact.Sak to "0x" + hexByte(it.sak.toInt())
            }
        }
        read {
            MifareClassic.get(tag)?.let {
                val type = when (it.type) {
                    MifareClassic.TYPE_CLASSIC -> "Classic"
                    MifareClassic.TYPE_PLUS -> "Plus"
                    MifareClassic.TYPE_PRO -> "Pro"
                    else -> null
                }
                type?.let { facts += NfcFact.MifareClassic to it }
                facts += NfcFact.MemoryBytes to it.size.toString()
                facts += NfcFact.Sectors to it.sectorCount.toString()
            }
        }
        read {
            MifareUltralight.get(tag)?.let {
                val type = when (it.type) {
                    MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
                    MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
                    else -> null
                }
                type?.let { facts += NfcFact.MifareUltralight to it }
            }
        }
        read {
            IsoDep.get(tag)?.let { iso ->
                iso.historicalBytes?.takeIf { it.isNotEmpty() }?.let { facts += NfcFact.HistoricalBytes to it.toHex() }
                iso.hiLayerResponse?.takeIf { it.isNotEmpty() }?.let { facts += NfcFact.HigherLayer to it.toHex() }
                facts += NfcFact.MaxTransfer to iso.maxTransceiveLength.toString()
            }
        }
        read {
            NfcB.get(tag)?.let {
                facts += NfcFact.ApplicationData to it.applicationData.toHex()
                facts += NfcFact.ProtocolInfo to it.protocolInfo.toHex()
            }
        }
        read {
            NfcF.get(tag)?.let {
                facts += NfcFact.Manufacturer to it.manufacturer.toHex()
                facts += NfcFact.SystemCode to it.systemCode.toHex()
            }
        }
        read { NfcV.get(tag)?.let { facts += NfcFact.Dsfid to "0x" + hexByte(it.dsfId.toInt()) } }
        var ndef: NdefInfo? = null
        read {
            Ndef.get(tag)?.let {
                // The message Android read when the tag arrived. Reading it again would need the tag in range.
                val records = it.cachedNdefMessage?.toByteArray()?.let(::parseNdefMessage).orEmpty()
                ndef = NdefInfo(it.type.substringAfterLast('.'), it.maxSize, it.isWritable, records)
            }
        }
        return NfcTag(
            id = tag.id.toHex(),
            techs = tag.techList.map { it.substringAfterLast('.') },
            facts = facts,
            ndef = ndef,
            readAt = System.currentTimeMillis(),
            notes = notes,
            card = card,
            cardProblem = cardProblem.takeIf { card?.number == null },
        )
    }

    /**
     * Reads a bank card's number and expiry date the way a contactless terminal does: select a payment
     * application, ask for its processing options, then read the records those name. No payment is made.
     *
     * Cards differ, so this tries hard: every application in turn, other reader abilities when the card refuses
     * the first, and reading records directly when the card names none. Answers that need the card to compute
     * a cryptogram can take a second or more, so the wait is longer than for the identification commands.
     */
    private fun readCard(card: IsoDep, applications: List<ByteArray>): Pair<CardDetails?, String?> {
        card.timeout = CARD_TIMEOUT_MILLIS
        var best: CardDetails? = null
        var problem: String? = CardProblem.NO_APPLICATION
        for (aid in applications) {
            var selection = ByteArray(0)
            // Each attempt starts with selecting the application again: after a refusal, many cards will not
            // answer another request until they are.
            val options = ProcessingAttempts.asSequence().map { (abilities, amount) ->
                selection = card.transceive(selectApplication(aid))
                if (!cardAnswered(selection)) return@map selection
                card.transceive(processingOptions(selection, readerAbilities = abilities, amount = amount))
            }.onEach { answer -> if (!cardAnswered(answer)) problem = statusWord(answer) }
                .firstOrNull(::cardAnswered)
            if (!cardAnswered(selection)) continue
            val answers = mutableListOf(selection)
            options?.let { answers += it }
            val ranges = options?.let(::recordRanges).orEmpty()
            if (ranges.isNotEmpty()) {
                ranges.asSequence()
                    .flatMap { range -> (range.first..range.last).asSequence().map { range.file to it } }
                    .take(MAX_RECORDS)
                    .forEach { (file, record) -> answers += card.transceive(readRecord(file, record)) }
            } else {
                answers += readRecordsDirectly(card)
            }
            val details = cardDetails(answers)
            if (details?.number != null) return details to null
            best = best ?: details
            if (options != null) problem = CardProblem.NO_NUMBER
        }
        return best to problem
    }

    /** Reads records file by file until each file runs out, for cards that do not say where their data is. */
    private fun readRecordsDirectly(card: IsoDep): List<ByteArray> {
        val answers = mutableListOf<ByteArray>()
        for (file in 1..DIRECT_FILES) {
            for (record in 1..DIRECT_RECORDS) {
                val answer = card.transceive(readRecord(file, record))
                if (noSuchRecord(answer)) break
                if (cardAnswered(answer)) answers += answer
                if (cardDetails(answers)?.number != null) return answers
            }
        }
        return answers
    }

    /**
     * Asks the tag the identification questions that suit it. Each answer is optional: a tag that does not
     * understand one, or leaves the field, simply adds nothing.
     */
    private fun paymentFacts(
        apps: List<PaymentApp>,
        facts: MutableList<Pair<NfcFact, String>>,
        notes: MutableSet<NfcNote>,
    ) {
        notes += NfcNote.PaymentCard
        facts.add(0, NfcFact.PaymentNetwork to apps.map { it.network }.distinct().joinToString(", "))
        apps.mapNotNull { it.label }.distinct().takeIf { it.isNotEmpty() }?.let {
            facts.add(1, NfcFact.CardName to it.joinToString(", "))
        }
    }

    /** Asks a smart card whether it is a MIFARE DESFire; adds its facts and returns true when it is. */
    private fun desfire(
        card: IsoDep,
        facts: MutableList<Pair<NfcFact, String>>,
        notes: MutableSet<NfcNote>,
    ): Boolean {
        val version = card.transceive(DESFIRE_GET_VERSION)
        if (!version.endsWith(MORE_FRAMES)) return false
        desfireVersion(version.copyOf(version.size - 2))?.let { (name, memory) ->
            facts.add(0, NfcFact.Chip to name)
            facts += NfcFact.MemoryBytes to memory.toString()
        }
        // The rest of the version answer comes in two more parts; they are not needed.
        repeat(2) { card.transceive(DESFIRE_CONTINUE) }
        val ids = card.transceive(DESFIRE_GET_APPLICATION_IDS)
        if (ids.endsWith(DESFIRE_OK)) {
            facts += NfcFact.Applications to ((ids.size - 2) / 3).toString()
        } else {
            notes += NfcNote.AppsLocked
        }
        return true
    }

    private fun identify(
        tag: Tag,
        facts: MutableList<Pair<NfcFact, String>>,
        notes: MutableSet<NfcNote>,
        read: (() -> Unit) -> Unit,
        onPaymentCard: (IsoDep, applications: List<ByteArray>) -> Unit,
    ) {
        val techs = tag.techList.toSet()
        if (MifareClassic::class.java.name in techs) notes += NfcNote.LockedByKeys
        // NXP stickers and tickets: one GET_VERSION command.
        if (MifareUltralight::class.java.name in techs || NfcA.get(tag)?.sak?.toInt() == 0) {
            read {
                NfcA.get(tag)?.use { nfcA ->
                    nfcA.connect()
                    nfcA.timeout = COMMAND_TIMEOUT_MILLIS
                    nxpChip(nfcA.transceive(byteArrayOf(0x60)))?.let { chip ->
                        facts.add(0, NfcFact.Chip to chip.name)
                        chip.userMemory?.let { facts += NfcFact.MemoryBytes to it.toString() }
                    }
                }
            }
        }
        // Smart cards: a bank card's payment directory first (the most common card), then DESFire's own command,
        // then the well-known payment applications one by one, for the few bank cards without a directory.
        read {
            IsoDep.get(tag)?.use { card ->
                card.connect()
                card.timeout = COMMAND_TIMEOUT_MILLIS
                val directory = card.transceive(SELECT_PAYMENT_DIRECTORY)
                val apps = paymentApps(directory)
                when {
                    apps.isNotEmpty() -> {
                        paymentFacts(apps, facts, notes)
                        // Last, so a card taken away while its details are read keeps what was found above.
                        onPaymentCard(card, applicationIds(directory))
                    }
                    !desfire(card, facts, notes) -> {
                        val found = KnownPaymentApplications.firstOrNull { aid ->
                            cardAnswered(card.transceive(selectApplication(aid)))
                        }
                        if (found != null) {
                            paymentFacts(listOf(PaymentApp(paymentNetwork(found), null)), facts, notes)
                            onPaymentCard(card, listOf(found))
                        }
                    }
                }
            }
        }
        // Library, ski-pass, and industrial tags: Get System Information, addressed to this tag.
        if (NfcV::class.java.name in techs) {
            read {
                val answer = NfcV.get(tag)?.use { vicinity ->
                    vicinity.connect()
                    vicinity.transceive(byteArrayOf(0x22, 0x2B) + tag.id)
                }
                val info = vicinityInfo(tag.id, answer)
                info.manufacturer?.let { facts.add(0, NfcFact.ChipMaker to it) }
                info.memory?.let { facts += NfcFact.MemoryBytes to it.toString() }
            }
        }
    }

    private companion object {
        const val COMMAND_TIMEOUT_MILLIS = 600
        const val MAX_RECORDS = 20
        const val CARD_TIMEOUT_MILLIS = 3_000
        const val DIRECT_FILES = 10
        const val DIRECT_RECORDS = 10
        val MORE_FRAMES = byteArrayOf(0x91.toByte(), 0xAF.toByte())
        val DESFIRE_OK = byteArrayOf(0x91.toByte(), 0x00)
        // DESFire native commands wrapped in ISO 7816 form: class 0x90, the command, no data.
        val DESFIRE_GET_VERSION = byteArrayOf(0x90.toByte(), 0x60, 0, 0, 0)
        val DESFIRE_CONTINUE = byteArrayOf(0x90.toByte(), 0xAF.toByte(), 0, 0, 0)
        val DESFIRE_GET_APPLICATION_IDS = byteArrayOf(0x90.toByte(), 0x6A, 0, 0, 0)

        /** SELECT by name of "2PAY.SYS.DDF01", the contactless payment directory. */
        val SELECT_PAYMENT_DIRECTORY = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E) +
            "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00)

        const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
    }
}

/** Bytes as hex pairs separated by colons, such as "04:A2:3B". */
fun ByteArray.toHex(): String = joinToString(":") { hexByte(it.toInt()) }

private fun hexByte(value: Int): String = (value and 0xFF).toString(16).padStart(2, '0').uppercase()

private fun ByteArray.endsWith(suffix: ByteArray): Boolean =
    size >= suffix.size && copyOfRange(size - suffix.size, size).contentEquals(suffix)
