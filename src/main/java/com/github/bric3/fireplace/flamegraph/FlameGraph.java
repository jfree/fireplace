/*
 * Fireplace
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.bric3.fireplace.flamegraph;

import com.github.bric3.fireplace.ui.BalloonToolTip;
import com.github.bric3.fireplace.ui.JScrollPaneWithButton;
import com.github.bric3.fireplace.ui.MouseInputListenerWorkaroundForToolTipEnabledComponent;
import org.openjdk.jmc.flightrecorder.stacktrace.tree.Node;
import org.openjdk.jmc.flightrecorder.stacktrace.tree.StacktraceTreeModel;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.function.Function;
import java.util.function.Supplier;

public class FlameGraph {
    public final FlameGraphPainter flameGraphPainter;
    private final FlameGraphCanvas canvas;
    public final JLayer<JScrollPane> component;

    public FlameGraph(Supplier<StacktraceTreeModel> stacktraceTreeModelSupplier, Function<FrameBox<Node>, String> extractToolTip) {
        flameGraphPainter = new FlameGraphPainter(stacktraceTreeModelSupplier);
        canvas = new FlameGraphCanvas(flameGraphPainter);
        ToolTipManager.sharedInstance().registerComponent(canvas);

        component = JScrollPaneWithButton.create(
                canvas,
                scrollPane -> {
                    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
                    scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
                    new ScrollPaneMouseListener(flameGraphPainter, extractToolTip).install(scrollPane);
                    new MouseInputListenerWorkaroundForToolTipEnabledComponent(scrollPane).install(canvas);
                }
        );
    }

    static class ScrollPaneMouseListener implements MouseInputListener {
        private Point pressedPoint;
        private final FlameGraphPainter flameGraph;
        private Function<FrameBox<Node>, String> extractToolTip;

        public ScrollPaneMouseListener(FlameGraphPainter flameGraph, Function<FrameBox<Node>, String> extractToolTip) {
            this.flameGraph = flameGraph;
            this.extractToolTip = extractToolTip;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if ((e.getSource() instanceof JScrollPane) && pressedPoint != null) {
                var scrollPane = (JScrollPane) e.getComponent();
                var viewPort = scrollPane.getViewport();
                if (viewPort == null) {
                    return;
                }

                var dx = e.getX() - pressedPoint.x;
                var dy = e.getY() - pressedPoint.y;
                var viewPortViewPosition = viewPort.getViewPosition();
                viewPort.setViewPosition(new Point(Math.max(0, viewPortViewPosition.x - dx),
                                                   Math.max(0, viewPortViewPosition.y - dy)));
                pressedPoint = e.getPoint();
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                this.pressedPoint = e.getPoint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            pressedPoint = null;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }

            if (e.getClickCount() == 2) {
                var scrollPane = (JScrollPane) e.getComponent();
                var viewPort = scrollPane.getViewport();

                var point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), viewPort.getView());

                flameGraph.zoomToFrameAt(
                        (Graphics2D) viewPort.getView().getGraphics(),
                        point
                ).ifPresent(zoomPoint -> {
                    scrollPane.revalidate();
                    EventQueue.invokeLater(() -> viewPort.setViewPosition(zoomPoint));
                });

                return;
            }

            if ((e.getSource() instanceof JScrollPane)) {
                var scrollPane = (JScrollPane) e.getComponent();
                var viewPort = scrollPane.getViewport();

                var point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), viewPort.getView());

                flameGraph.toggleSelectedFrameAt(
                        (Graphics2D) viewPort.getView().getGraphics(),
                        point,
                        viewPort.getViewRect()
                );

                scrollPane.repaint();
            }
        }

        @Override
        public void mouseEntered(MouseEvent e) {}

        @Override
        public void mouseExited(MouseEvent e) {
            if ((e.getSource() instanceof JScrollPane)) {
                var scrollPane = (JScrollPane) e.getComponent();
                flameGraph.stopHover();
                scrollPane.repaint();
            }

        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if ((e.getSource() instanceof JScrollPane)) {
                var scrollPane = (JScrollPane) e.getComponent();
                var viewPort = scrollPane.getViewport();

                var view = (JComponent) viewPort.getView();
                var point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), view);

                flameGraph.hoverFrameAt(
                        (Graphics2D) view.getGraphics(),
                        point,
                        frame -> view.setToolTipText(extractToolTip.apply(frame))
                );

                scrollPane.repaint();
            }
        }

        public void install(JScrollPane sp) {
            sp.addMouseListener(this);
            sp.addMouseMotionListener(this);
        }

    }

    private static class FlameGraphCanvas extends JPanel {
        private BalloonToolTip toolTip;
        private final FlameGraphPainter flameGraphPainter;

        public FlameGraphCanvas(FlameGraphPainter flameGraphPainter) {
            this.flameGraphPainter = flameGraphPainter;
        }


        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d = (d == null) ? new Dimension(400, 400) : d;

            Insets insets = getInsets();
            var flameGraphDimension = flameGraphPainter.getFlameGraphDimension((Graphics2D) getGraphics(),
                                                                               getSize(),
                                                                               getVisibleRect(),
                                                                               insets
            );
            d.width = Math.max(d.width, flameGraphDimension.width + insets.left + insets.right);
            d.height = Math.max(d.height, flameGraphDimension.height + insets.top + insets.bottom);

            // When the preferred size is discovered, also set the actual
            // canvas size, as it is needed during `viewPort.setViewPosition`
            // if it is not then the viewport will not be able to scroll to the
            // when the flamegraph is zoomed (ie it's diemnsions change)
            setSize(d);
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            flameGraphPainter.paint((Graphics2D) g, getWidth(), getHeight(), getVisibleRect());
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            return super.getToolTipText(e);
        }

        @Override
        public JToolTip createToolTip() {
            //                var toolTip = super.createToolTip();
            if (toolTip == null) {
                toolTip = new BalloonToolTip();
                toolTip.setComponent(this);
            }

            return toolTip;
        }

    }
}
