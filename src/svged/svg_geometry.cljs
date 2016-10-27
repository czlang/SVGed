(ns svged.svg-geometry)

(defprotocol IPoint
  (x [p])
  (y [p]))

(deftype Point [x-coord y-coord]
  IPoint
  (x [_] x-coord)
  (y [_] y-coord))

(defn point [x y]
  (Point. x y))
