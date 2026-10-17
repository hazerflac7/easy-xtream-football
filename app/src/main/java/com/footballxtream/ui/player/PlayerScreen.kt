
@Composable
private fun EpgOverlay(now: String, next: String?, modifier: Modifier = Modifier) {
    val nowText = stringResource(R.string.epg_now, now)
    val nextText = if (!next.isNullOrBlank()) stringResource(R.string.epg_next_suffix, next) else ""
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x990A0E12))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = nowText + nextText,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xCCE6EAEE),
        )
    }
}

/**
 * A help line drawn as a card of chips instead of one long string of text. Used for the touch and
 * the remote control legends and for the menu's navigation hint, so every device gets the same look.
 *
 * It reuses the existing strings rather than adding new ones to all 24 locales: every translation is
 * written as `gesture: action  ·  gesture: action  …`, so each "·" fragment becomes a chip and the
 * part before the colon is picked out as the gesture, shown in the brand green. The chips match the
 * section tabs of the menu sheet, so the hint looks like part of the app and not like a debug string
 * printed over the video. A fragment without a colon (the navigation hint is written that way in
 * several locales) simply renders whole.
 */
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HintChips(legend: String, maxWidth: Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val items = remember(legend) {
        legend.split('·').mapNotNull { fragment ->
            val text = fragment.trim()
            if (text.isEmpty()) return@mapNotNull null
            // ':' in most locales, '：' in Chinese; French writes " : ", which trim() handles.
            val halves = text.split(':', '：', limit = 2)
            if (halves.size == 2 && halves[1].isNotBlank()) {
                halves[0].trim() to halves[1].trim()
            } else {
                "" to text
            }
        }
    }
    // Deliberately the same recipe as StatsOverlay (the channel info): 8 dp corners, the same
    // translucent black, the same 10/5 padding and the same labelSmall body with the accent in
    // labelMedium green. Each fragment is its own pill, with no card around them, so a hint sitting
    // next to the channel info reads as the same kind of object instead of a heavier, darker block.
    FlowRow(
        modifier = modifier.widthIn(max = maxWidth),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (gesture, action) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x990A0E12))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (gesture.isNotEmpty()) {
                    Text(
                        text = gesture,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        maxLines = 1,
                    )
                }
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xCCE6EAEE),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The touch (phone/tablet) form of the OK menu: a full-width sheet pinned to the bottom edge.
 *
 * It replaces the 280 dp card, which in portrait took most of the width and left the help lines and
 * the coffee card fighting for the same bottom-right corner. Here every section is a tappable tab, so
 * nothing has to be explained in writing, and the Café section is a row inside the sheet instead of a
 * card floating over it. Swiping across the sheet still changes section, and so does the D-pad on the
 * rare touch device that has one, because the key handler is untouched.
 */
@Composable
private fun TouchMenuSheet(
    sections: List<String>,
    sectionIndex: Int,
    options: List<String>,
    selectedIndex: Int,
    showCoffeeCard: Boolean,
    onSelectSection: (Int) -> Unit,
    onSelectOption: (Int) -> Unit,
    onStepSection: (Int) -> Unit,
    onOpenCoffee: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xF20A0E12))
            // Swallow taps: without this a tap on the sheet's own background would reach the
            // full-screen gesture layer underneath and close the menu the user just opened.
            .pointerInput(Unit) { detectTapGestures { } }
            .sectionSwipe(onStepSection)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Grabber: says "this panel belongs to the bottom edge" without a word of text.
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x66FFFFFF)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sections.forEachIndexed { index, label ->
                val current = index == sectionIndex
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (current) colors.onPrimary else Color(0xFFE6EAEE),
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (current) colors.primary else Color(0x1AFFFFFF))
                        .clickable { onSelectSection(index) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        // The list is capped at 40 % of the screen so the sheet never swallows the picture. That
        // matters in landscape, where 260 dp of options alone is most of the height.
        val listMax = (LocalConfiguration.current.screenHeightDp * 0.4f).dp.coerceAtMost(260.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = listMax)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showCoffeeCard) {
                // No billing (Amazon builds, or Play unavailable): the QR is the whole section, so it
                // rides inside the sheet at full size instead of as a card on top of it.
                CoffeeCard(
                    showQr = true,
                    compact = false,
                    onOpenCoffee = onOpenCoffee,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                options.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    Text(
                        text = (if (selected) "●  " else "○  ") + label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) colors.primary else Color(0xFFE6EAEE),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        // 48 dp-tall rows: the old 2 dp padding gave a target far under the minimum.
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectOption(index) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Horizontal swipe on the menu card itself, to change section.
 *
 * The full-screen gesture layer lives *below* the overlays in z-order, and Compose stops hit-testing
 * at the topmost sibling it hits, so a swipe that starts on the menu card never reached it: on a phone
 * the card covers the bottom-left corner and its option rows are `clickable`, which is exactly where
 * a thumb lands. The card therefore carries its own detector. It sits on the card (the parent), not on
 * the rows, so it still sees the drag after the rows decline it: `clickable` cancels its press on slop
 * without consuming the movement, and the Main pass travels child → parent.
 */
private fun Modifier.sectionSwipe(onStepSection: (Int) -> Unit): Modifier = this.pointerInput(Unit) {
    // PointerInputScope is a Density, so the threshold is resolved here without a composed{} wrapper.
    val threshold = 40.dp.toPx()
    var dx = 0f
    detectHorizontalDragGestures(
        onDragStart = { dx = 0f },
        onHorizontalDrag = { change, amount -> change.consume(); dx += amount },
        onDragEnd = { if (abs(dx) >= threshold) onStepSection(if (dx < 0) 1 else -1) },
    )
}

/**
 * "‹ Sección ›" header. The arrows are real tap targets on touch screens (they looked tappable and
 * were not, which is half of why the menu felt broken on a phone); on TV they stay decorative so they
 * never become focusable and steal the D-pad from the player's key handler.
 */
@Composable
private fun SectionHeader(section: String, onStepSection: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val touch = !isTv()
    val arrow: @Composable (String, Int) -> Unit = { glyph, step ->
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary,
            modifier = Modifier
                .then(if (touch) Modifier.clickable { onStepSection(step) } else Modifier)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        arrow("‹", -1)
        Text(
            text = section,
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        arrow("›", 1)
    }
}

@Composable
private fun OptionsMenu(
    section: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onStepSection: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .width(280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE60A0E12))
            .sectionSwipe(onStepSection)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionHeader(section = section, onStepSection = onStepSection)
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                text = (if (selected) "● " else "○ ") + label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.primary else Color(0xFFE6EAEE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Tappable on touch screens; a full-width row so the target isn't just the text.
                modifier = Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(vertical = 2.dp),
            )
        }
    }
}

/** The "Café" OK-menu section's bottom-left panel: same card format as the other sections, but it only
 *  carries the section header and how to turn the reminder off — the QR rides in the shared [CoffeeCard]
 *  that slides in bottom-right (see the AnimatedVisibility in the player). */
@Composable
private fun CoffeeMenuPanel(
    section: String,
    onStepSection: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE60A0E12))
            .sectionSwipe(onStepSection)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionHeader(section = section, onStepSection = onStepSection)
        Text(
            text = stringResource(R.string.coffee_disable_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xCCE6EAEE),
        )
    }
}

/** Shared Ko-fi card (QR + invite + thanks) used by both the timed reminder "bug" and the OK-menu
 *  "Café" section, so they look and animate identically. */
@Composable
private fun CoffeeCard(
    showQr: Boolean,
    compact: Boolean,
    onOpenCoffee: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val openSite = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.support_site_url))),
            )
        }
        Unit
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE60A0E12)) // same opacity as the OK menu
            // With Play billing the whole card is the button (tap on a phone, OK on TV — see the key
            // handler). With the QR there is nothing to open: the QR keeps its own tap → Ko-fi.
            .then(if (showQr || !compact) Modifier else Modifier.clickable { onOpenCoffee() })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The QR comes first: it is what the viewer is meant to scan with a phone. It opens the app's
        // own site (which invites to Ko-fi), never a payment page. On a phone you can't scan your own
        // screen, so it only shows where it is the only way to give, and a tap opens the site instead.
        if (showQr || isTv()) {
            Image(
                painter = painterResource(R.drawable.qr_site),
                contentDescription = stringResource(R.string.support_qr_desc),
                modifier = Modifier
                    .size(if (compact) 88.dp else 148.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { openSite() }
                    .background(Color.White)
                    .padding(5.dp),
            )
        }
        // The cup rides along only in the floating reminder, at the QR's size so neither dwarfs the
        // other. In the open Café section the prices are already on screen next to it.
        if (!showQr && compact) {
            Image(
                painter = painterResource(R.drawable.ic_coffee),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(88.dp),
            )
        }
        Column(
            modifier = Modifier.widthIn(min = 160.dp, max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
    }
}
