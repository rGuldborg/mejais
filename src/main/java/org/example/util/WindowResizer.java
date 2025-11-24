package org.example.util;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * Adds basic edge-drag resizing for transparent windows.
 */
public final class WindowResizer {
    private static final double BORDER = 8.0;

    private WindowResizer() {
    }

    public static void makeResizable(Stage stage, Region region) {
        ResizeHandler handler = new ResizeHandler(stage, region);
        region.addEventFilter(MouseEvent.MOUSE_MOVED, handler);
        region.addEventFilter(MouseEvent.MOUSE_PRESSED, handler);
        region.addEventFilter(MouseEvent.MOUSE_DRAGGED, handler);
        region.addEventFilter(MouseEvent.MOUSE_RELEASED, handler);
    }

    private static final class ResizeHandler implements EventHandler<MouseEvent> {
        private final Stage stage;
        private final Region region;
        private Cursor dragCursor = Cursor.DEFAULT;
        private double startX;
        private double startY;
        private double startWidth;
        private double startHeight;
        private double stageX;
        private double stageY;

        private ResizeHandler(Stage stage, Region region) {
            this.stage = stage;
            this.region = region;
        }

        @Override
        public void handle(MouseEvent event) {
            if (!stage.isResizable() || stage.isMaximized()) {
                region.setCursor(Cursor.DEFAULT);
                return;
            }
            if (event.getEventType() == MouseEvent.MOUSE_MOVED) {
                updateCursor(event);
            } else if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
                beginDrag(event);
            } else if (event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
                resize(event);
            } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED) {
                dragCursor = Cursor.DEFAULT;
                region.setCursor(Cursor.DEFAULT);
            }
        }

        private void updateCursor(MouseEvent event) {
            Cursor cursor = determineCursor(event);
            region.setCursor(cursor);
        }

        private Cursor determineCursor(MouseEvent event) {
            double x = event.getX();
            double y = event.getY();
            double width = region.getWidth();
            double height = region.getHeight();

            boolean left = x < BORDER;
            boolean right = x > width - BORDER;
            boolean top = y < BORDER;
            boolean bottom = y > height - BORDER;

            if (left && top) return Cursor.NW_RESIZE;
            if (left && bottom) return Cursor.SW_RESIZE;
            if (right && top) return Cursor.NE_RESIZE;
            if (right && bottom) return Cursor.SE_RESIZE;
            if (right) return Cursor.E_RESIZE;
            if (left) return Cursor.W_RESIZE;
            if (top) return Cursor.N_RESIZE;
            if (bottom) return Cursor.S_RESIZE;
            return Cursor.DEFAULT;
        }

        private void beginDrag(MouseEvent event) {
            dragCursor = region.getCursor();
            startX = event.getScreenX();
            startY = event.getScreenY();
            startWidth = stage.getWidth();
            startHeight = stage.getHeight();
            stageX = stage.getX();
            stageY = stage.getY();
        }

        private void resize(MouseEvent event) {
            if (dragCursor == Cursor.DEFAULT) {
                return;
            }
            double deltaX = event.getScreenX() - startX;
            double deltaY = event.getScreenY() - startY;
            double minWidth = stage.getMinWidth() > 0 ? stage.getMinWidth() : 400;
            double minHeight = stage.getMinHeight() > 0 ? stage.getMinHeight() : 300;

            if (dragCursor == Cursor.E_RESIZE || dragCursor == Cursor.NE_RESIZE || dragCursor == Cursor.SE_RESIZE) {
                double width = Math.max(minWidth, startWidth + deltaX);
                stage.setWidth(width);
            }
            if (dragCursor == Cursor.W_RESIZE || dragCursor == Cursor.NW_RESIZE || dragCursor == Cursor.SW_RESIZE) {
                double width = Math.max(minWidth, startWidth - deltaX);
                double widthDelta = startWidth - width;
                stage.setWidth(width);
                stage.setX(stageX + widthDelta);
            }
            if (dragCursor == Cursor.S_RESIZE || dragCursor == Cursor.SE_RESIZE || dragCursor == Cursor.SW_RESIZE) {
                double height = Math.max(minHeight, startHeight + deltaY);
                stage.setHeight(height);
            }
            if (dragCursor == Cursor.N_RESIZE || dragCursor == Cursor.NE_RESIZE || dragCursor == Cursor.NW_RESIZE) {
                double height = Math.max(minHeight, startHeight - deltaY);
                double heightDelta = startHeight - height;
                stage.setHeight(height);
                stage.setY(stageY + heightDelta);
            }
        }
    }
}
