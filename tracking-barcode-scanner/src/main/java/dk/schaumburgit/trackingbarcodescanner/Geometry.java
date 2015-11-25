package dk.schaumburgit.trackingbarcodescanner;

/**
 * Created by Thomas on 17-11-2015.
 */
public class Geometry
{
    public static class Rectangle {
        public int x;
        public int y;
        public int width;
        public int height;

        public Rectangle(Rectangle that)
        {
            this.x = that.x;
            this.y = that.y;
            this.width = that.width;
            this.height = that.height;
        }

        public Rectangle(int x, int y)
        {
            this.x = x;
            this.y = y;
            width = 0;
            height = 0;
        }

        public Rectangle expandToInclude (int includeX, int includeY) {
            Rectangle result = new Rectangle(this);
            int right = result.x + result.width;;
            int top = result.y + result.height;

            if (includeX < result.x) {
                // expand left:
                result.x = includeX;
                result.width = right - result.x;
            } else if (includeX > right) {
                // expand right:
                result.width = (includeX - result.x);
            }

            if (includeY < result.y)
            {
                // Expand downwards:
                result.y = includeY;
                result.height = top - result.y;
            } else if (y > top)
            {
                // Expand upwards:
                result.height = includeY - result.y;
            }

            return result;
        }

        public Rectangle normalize(int minX, int minY, int maxX, int maxY)
        {
            Rectangle result = new Rectangle(this);
            int right = result.x + result.width;;
            int top = result.y + result.height;

            // minY:
            if (result.y < minY)
            {
                result.y = minY;
                result.height = top - result.y;
            }

            // minX:
            if (result.x < minX)
            {
                result.x = minX;
                result.width = right - result.x;
            }

            // maxX:
            if (result.x  > maxX)
            {
                result.x = maxX;
                result.width = 0;
            } else if (right > maxX) {
                result.width = maxX - result.x;
            }

            // maxY:
            if (result.y > maxY)
            {
                result.y = maxY;
                result.height = 0;
            } else if (top > maxY) {
                result.height = maxY - result.y;
            }

            return result;
        }

        public Rectangle addRelativeMargin(double relativeMargin)
        {
            Rectangle result = new Rectangle(this);

            // Add right and left margins:
            if (result.width > 0)
            {
                int absoluteMarginX = (int)(result.width * relativeMargin);

                result.y -= absoluteMarginX;
                result.width += 2*absoluteMarginX;
            }

            // Add top and bottom margins:
            if (result.height > 0) {
                int absoluteMarginY = (int) (result.height * relativeMargin);

                result.x -= absoluteMarginY;
                result.height += 2 * absoluteMarginY;
            }

            // Restrict to 1st quadrant:
            return result.normalize(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
    }
}
