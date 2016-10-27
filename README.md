# SVGed

* Me, exploring SVG space...
* In progress... https://czlang.github.io/SVGed/

## Setup

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).
This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    lein clean

To create a production build run:

    lein do clean, cljsbuild once min

And open your browser in `resources/public/index.html`. You will not
get live reloading, nor a REPL. 


## Some reading
* http://worrydream.com/#!/DrawingDynamicVisualizationsTalkAddendum
* https://svg-edit.github.io/svgedit/releases/svg-edit-2.8.1/svg-editor.html
* https://github.com/SVG-Edit/svgedit
* http://vectorpaint.yaks.co.nz/
* http://tutorials.jenkov.com/svg/svg-transformation.html
* http://apike.ca/prog_svg_transform.html
* http://editor.method.ac/
* http://bazaar.launchpad.net/~inkscape.dev/inkscape/trunk/files/15191/src
* https://processing.org/tutorials/transform2d/
* https://sarasoueidan.com/blog/svg-transformations/
* http://gamedev.stackexchange.com/questions/86755/how-to-calculate-corner-marks-of-a-rotated-rectangle
