/*
 * Copyright 2021 Datadog, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.bric3.fireplace.flamegraph;

import io.github.bric3.fireplace.core.ui.Colors;

import java.awt.*;
import java.util.Objects;
import java.util.function.Function;

import static io.github.bric3.fireplace.flamegraph.FrameRenderingFlags.isFocusedFrame;
import static io.github.bric3.fireplace.flamegraph.FrameRenderingFlags.isFocusing;
import static io.github.bric3.fireplace.flamegraph.FrameRenderingFlags.isHighlightedFrame;
import static io.github.bric3.fireplace.flamegraph.FrameRenderingFlags.isHighlighting;
import static io.github.bric3.fireplace.flamegraph.FrameRenderingFlags.isHovered;

/**
 * Strategy for choosing the colors of a frame.
 *
 * @param <T> The type of the frame node (depends on the source of profiling data).
 */
@FunctionalInterface
public interface FrameColorProvider<T> {
    class ColorModel {
        public Color background;
        public Color foreground;

        /**
         * Data-structure that hold the computed colors for a frame.
         *
         * @param background The background color of the frame.
         * @param foreground The foreground color of the frame.
         */
        public ColorModel(Color background, Color foreground) {
            this.background = background;
            this.foreground = foreground;
        }

        public ColorModel set(Color background, Color foreground) {
            this.background = background;
            this.foreground = foreground;
            return this;
        }

        public ColorModel copy() {
            return new ColorModel(background, foreground);
        }
    }

    /**
     * Returns the color model for the given <code>frame</code> according to the given <code>flags</code>.
     *
     * <p>
     *     An implementation may choose to return the same instance of color model
     *     for all frames to save allocations.
     * </p>
     *
     * @param frame The frame
     * @param flags The flags
     * @return The color model for this frame
     */
    ColorModel getColors(FrameBox<T> frame, int flags) ;

    static <T> FrameColorProvider<T> defaultColorProvider(Function<FrameBox<T>, Color> frameBaseColorFunction) {
        Objects.requireNonNull(frameBaseColorFunction, "frameColorFunction");
        return new FrameColorProvider<>() {
            /**
             * The color used to draw frames that are highlighted.
             */
            private final Color highlightedColor = new Color(0xFFFFE771, true);

            private final ColorModel reusableDataStructure = new ColorModel(null, null);

            @Override
            public ColorModel getColors(FrameBox<T> frame, int flags) {
                Color baseBackgroundColor = frameBaseColorFunction.apply(frame);
                Color backgroundColor = baseBackgroundColor;

                if (isFocusing(flags) && !isFocusedFrame(flags)) {
                    backgroundColor = Colors.blend(baseBackgroundColor, Colors.translucent_black_80);
                }
                if (isHighlighting(flags)) {
                    backgroundColor = Colors.isDarkMode() ?
                            Colors.blend(backgroundColor, Colors.translucent_black_B0) :
                            Colors.blend(backgroundColor, Color.WHITE);
                    if (isHighlightedFrame(flags)) {
                        backgroundColor = baseBackgroundColor;
                    }
                }
                if (isHovered(flags)) {
                    backgroundColor = Colors.blend(backgroundColor, Colors.translucent_black_40);
                }

                return reusableDataStructure.set(
                        backgroundColor,
                        Colors.foregroundColor(backgroundColor)
                );
            }
        };
    }
}
