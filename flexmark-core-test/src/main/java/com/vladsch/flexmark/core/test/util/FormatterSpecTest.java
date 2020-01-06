package com.vladsch.flexmark.core.test.util;

import com.vladsch.flexmark.formatter.Formatter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.test.util.FlexmarkSpecExampleRenderer;
import com.vladsch.flexmark.test.util.SpecExampleRenderer;
import com.vladsch.flexmark.test.util.TestUtils;
import com.vladsch.flexmark.test.util.spec.SpecExample;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.format.TrackedOffset;
import com.vladsch.flexmark.util.misc.Pair;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import com.vladsch.flexmark.util.sequence.builder.SequenceBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class FormatterSpecTest extends FormatterTranslationSpecTestBase {
    public FormatterSpecTest(@NotNull SpecExample example, @Nullable Map<String, ? extends DataHolder> optionMap, @Nullable DataHolder... defaultOptions) {
        super(example, optionMap, defaultOptions);
    }

    @Override
    public @NotNull SpecExampleRenderer getSpecExampleRenderer(@NotNull SpecExample example, @Nullable DataHolder exampleOptions) {
        DataHolder combinedOptions = aggregate(myDefaultOptions, exampleOptions);

        return new FlexmarkSpecExampleRenderer(example, combinedOptions, Parser.builder(combinedOptions).build(), Formatter.builder(combinedOptions).build(), true) {
            ArrayList<TrackedOffset> trackedOffsets;
            BasedSequence trackedSequence;
            BasedSequence originalSequence;

            @Override
            public void parse(CharSequence input) {
                originalSequence = BasedSequence.of(input);
                Pair<BasedSequence, int[]> extractMarkup = TestUtils.extractMarkup(originalSequence);
                trackedSequence = extractMarkup.getFirst();
                super.parse(trackedSequence.toString());

                if (extractMarkup.getSecond().length > 0) {
                    trackedOffsets = new ArrayList<>(extractMarkup.getSecond().length);
                    char c = EDIT_OP_CHAR.get(myOptions);
                    int editOp = EDIT_OP.get(myOptions);

                    for (int offset : extractMarkup.getSecond()) {
                        trackedOffsets.add(TrackedOffset.track(offset, editOp != 0 && c == ' ', editOp > 0, editOp < 0));
                    }

                    Formatter.TRACKED_SEQUENCE.set(getDocument().getDocument(), trackedSequence);
                    Formatter.TRACKED_OFFSETS.set(getDocument().getDocument(), trackedOffsets);
                    Formatter.RESTORE_TRACKED_SPACES.set(getDocument().getDocument(), Formatter.RESTORE_TRACKED_SPACES.get(myOptions));
                }
            }

            @Override
            protected @NotNull String renderHtml() {
                if (trackedOffsets == null && !SHOW_LINE_RANGES.get(myOptions)) {
                    return getRenderer().render(getDocument());
                } else {
//                    SequenceBuilder builder = trackedSequence.getBuilder();
                    SequenceBuilder builder = getDocument().getChars().getBuilder();
                    getRenderer().render(getDocument(), builder);
                    String html = builder.toString();

                    List<TrackedOffset> trackedOffsetList = Formatter.TRACKED_OFFSETS.get(getDocument().getDocument());
                    assert trackedOffsetList == trackedOffsets;

                    int[] offsets = new int[trackedOffsets.size()];
                    int i = 0;

                    for (TrackedOffset trackedOffset : trackedOffsets) {
                        if (trackedOffset.isResolved()) {
                            offsets[i++] = trackedOffset.getIndex();
                        } else {
                            System.out.println(String.format("Offset %s is not resolved", trackedOffset.toString()));
                        }
                    }

                    if (i < offsets.length) {
                        offsets = Arrays.copyOf(offsets, i);
                    }

                    String result = TestUtils.insertCaretMarkup(BasedSequence.of(html), offsets).toString();

                    if (SHOW_LINE_RANGES.get(myOptions)) {
                        StringBuilder out = new StringBuilder();
                        out.append(result);
                        TestUtils.appendBanner(out, TestUtils.bannerText("Ranges"), false);
                        out.append(builder.toStringWithRanges(false));
                        result = out.toString();
                    }
                    return result;
                }
            }
        };
    }
}
