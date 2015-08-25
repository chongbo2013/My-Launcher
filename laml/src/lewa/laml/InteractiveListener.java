
package lewa.laml;

import lewa.laml.elements.ButtonScreenElement;
import lewa.laml.elements.ButtonScreenElement.ButtonAction;

public abstract interface InteractiveListener {
    public abstract void onButtonInteractive(ButtonScreenElement ele,
            ButtonAction action);
}
