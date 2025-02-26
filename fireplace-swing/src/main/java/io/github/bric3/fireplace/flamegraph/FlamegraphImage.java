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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Render a flamegraph or icicle to an image.
 *
 * <p>
 * Example usage:
 * <pre><code>
 * FlamegraphImage&lt;MyNode&gt; flamegraphImage = new FlamegraphView&lt;&gt;(
 *     frameTextProvider,                              // string representation candidates
 *     frameColorProvider,                             // color the frame
 *     frameFontProvider,                              // returns a given font for a frame
 * );
 * RenderedImage image = flamegraphImage.make(
 *     new FrameModel&lt;&gt;(...),                    // the frames model
 *     600,                                            // width of the image
 *     FlamegraphView.Mode.ICICLEGRAPH                 // the mode of the image
 * )
 *
 * ImageIO.write(image, "png", new File("flamegraph.png"));
 * </code></pre>
 *
 * @param <T> The type of the node data.
 * @see FlamegraphView
 */
public class FlamegraphImage<T> {
    private final FlamegraphRenderEngine<T> fre;

    /**
     * Create the flamegraph image util.
     *
     * <p>
     * The functions passed to the constructor define how to present the data:
     * <ul>
     *     <li>Possible string candidates to display in frames, those are
     *     selected based on the available space</li>
     *     <li>The root node text to display, if something specific is relevant,
     *     like the type of events, their number, etc.</li>
     *     <li>The frame background and foreground colors</li>
     * </ul>
     *
     * @param frameTextsProvider The function to display label in frames.
     * @param frameColorProvider The frame to background color function.
     * @param frameFontProvider  The frame font provider.
     */
    public FlamegraphImage(
            FrameTextsProvider<T> frameTextsProvider,
            FrameColorProvider<T> frameColorProvider,
            FrameFontProvider<T> frameFontProvider
    ) {
        this.fre = new FlamegraphRenderEngine<>(new FrameRenderer<>(
                Objects.requireNonNull(frameTextsProvider),
                Objects.requireNonNull(frameColorProvider),
                Objects.requireNonNull(frameFontProvider)
        ));
    }

    /**
     * Make an image from the frames models.
     *
     * @param frameModel The frame model to render.
     * @param mode       The display mode of the graph.
     * @param width      The wanted width of the image, the height is computed from this width.
     * @return The flamegraph rendered image.
     */
    public RenderedImage generate(FrameModel<T> frameModel, FlamegraphView.Mode mode, int width) {
        try {
            fre.init(Objects.requireNonNull(frameModel));

            var height = fre.computeVisibleFlamegraphHeight(
                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics(),
                    width
            );

            var imageCanvas = new BufferedImage(
                    width,
                    height,
                    BufferedImage.TYPE_INT_ARGB
            );
            var g2 = imageCanvas.createGraphics();
            // Make background transparent
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0, 0, imageCanvas.getWidth(), imageCanvas.getHeight());

            g2.setComposite(AlphaComposite.Src);
            fre.paintToImage(
                    g2,
                    new Rectangle(0, 0, imageCanvas.getWidth(), imageCanvas.getHeight()),
                    mode == FlamegraphView.Mode.ICICLEGRAPH
            );

            g2.dispose();
            return imageCanvas;
        } finally {
            fre.reset();
        }
    }

    /**
     * Lower level method to generate an image of the flamegraph.
     *
     * <p>
     * This methods assumes the client will control the lifecycle of the graphics
     * handle.
     * This method will first compute the height of the flamegraph, and
     * it will use the passed graphics handle to get the appropriate font metrics.
     * Then the {@code onHeightComputed} function will be called with the height.
     * And finally, the renderer will use the graphics handle to draw the graph.
     * </p>
     *
     * @param frameModel       The frame model to render.
     * @param mode             The display mode of the graph.
     * @param width            The wanted width of the image, the height is computed from this width.
     * @param g2               The graphics context to render to.
     * @param onHeightComputed Callback when the height has been computed.
     */
    public void generate(
            FrameModel<T> frameModel,
            FlamegraphView.Mode mode,
            int width,
            Graphics2D g2,
            IntConsumer onHeightComputed
    ) {
        try {
            fre.init(Objects.requireNonNull(frameModel));

            var height = fre.computeVisibleFlamegraphHeight(
                    g2,
                    width
            );
            onHeightComputed.accept(height);

            fre.paintToImage(
                    g2,
                    new Rectangle(0, 0, width, height),
                    mode == FlamegraphView.Mode.ICICLEGRAPH
            );
        } finally {
            fre.reset();
        }
    }
}
