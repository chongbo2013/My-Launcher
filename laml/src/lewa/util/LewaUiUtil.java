package lewa.util;

import android.R;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import java.util.HashSet;
import android.content.res.TypedArray;

public class LewaUiUtil
{
    static SparseArray<Integer> sStateAttributeIndexes;
    static SparseArray<int[]> sViewStates;
    
    static SimplePool.PoolInstance sTypedValuePool = SimplePool.newInsance(new SimplePool.Manager() {

        public TypedValue createInstance()
        {
            return new TypedValue();
        }

    }, 4);

    static {
        sViewStates = new SparseArray();
        sStateAttributeIndexes = new SparseArray();
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_focused, Integer.valueOf(1));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_enabled, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_checkable, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_checked, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_selected, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_active, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_single, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_first, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_middle, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_last, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_pressed, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.ExpandableListGroupIndicatorState_state_empty, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(R.styleable.DrawableStates_state_activated, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(lewa.R.styleable.DrawableStates_state_single, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(lewa.R.styleable.DrawableStates_state_first, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(lewa.R.styleable.DrawableStates_state_middle, Integer.valueOf(1 << sStateAttributeIndexes.size()));
        sStateAttributeIndexes.put(lewa.R.styleable.DrawableStates_state_last, Integer.valueOf(1 << sStateAttributeIndexes.size()));
    }

    public LewaUiUtil() {
    }

    public static boolean getBoolean(Context context, int attrId, boolean defValue)
    {
        TypedValue typedValue = (TypedValue)sTypedValuePool.acquire();
        boolean ret = defValue;
        if(context.getTheme().resolveAttribute(attrId, typedValue, true)) {
            if(typedValue.type != TypedValue.TYPE_INT_BOOLEAN) {
                ret = false;
            } else if(typedValue.data != TypedValue.TYPE_NULL) {
                ret = true;
            } else {
                ret = false;
            }
        }
        sTypedValuePool.release(typedValue);
        return ret;
    }

    public static int getColor(Context context, int attrId)
    {
        return context.getResources().getColor(resolveAttribute(context, attrId));
    }

    public static Drawable getDrawable(Context context, int attrId)
    {
        int id = resolveAttribute(context, attrId);
        if(id > 0)
            return context.getResources().getDrawable(id);
        else
            return null;
    }
    
    public static int getLewaUiVersion(Context context) {
        int ret = -1;
        TypedValue typedValue = (TypedValue)sTypedValuePool.acquire();
        if(context.getTheme().resolveAttribute(com.lewa.internal.R.attr.lewa_ui_version, typedValue, true))
            ret = typedValue.data;
        sTypedValuePool.release(typedValue);
        return ret;
    }

    public static boolean isV5Ui(Context context) {
        return getLewaUiVersion(context) == 5;
    }

    private static boolean getLewaColorfulStyleValue(Context context) {
        boolean ret = false;
        TypedValue typedValue = (TypedValue)sTypedValuePool.acquire();
        if (context.getTheme().resolveAttribute(com.lewa.internal.R.attr.lewa_enable_colorful_style, typedValue, true)) {
            if (typedValue.type == TypedValue.TYPE_INT_BOOLEAN) {
                ret = typedValue.data != 0;
            }
        }

        sTypedValuePool.release(typedValue);

        return ret;
    }

    public static boolean isEnabledColorfulStyle(Context context) {
        return getLewaColorfulStyleValue(context);
    }

    public static boolean isImmersiveStatusbar(Context context) {
		if (!isLewaUi(context)) {
			return false;
		}
        boolean ret = false;
        /*
        TypedValue typedValue = (TypedValue)sTypedValuePool.acquire();
        if (context.getTheme().resolveAttribute(com.lewa.internal.R.attr.lewa_immersive_statusbar, typedValue, false)) {
            if (typedValue.type == TypedValue.TYPE_INT_BOOLEAN) {
                ret = typedValue.data != 0;
            }
        }

        sTypedValuePool.release(typedValue);
        */

        return ret;
    }

	public static boolean isActionBarOverlay(Context context) {
		if (!isLewaUi(context)) {
			return false;
		}
        boolean ret = false;
        TypedValue typedValue = (TypedValue)sTypedValuePool.acquire();
        if (context.getTheme().resolveAttribute(com.android.internal.R.attr.windowActionBarOverlay, typedValue, false)) {
            if (typedValue.type == TypedValue.TYPE_INT_BOOLEAN) {
                ret = typedValue.data != 0;
            }
        }

        sTypedValuePool.release(typedValue);

        return ret;
	}

	public static boolean isV6Ui(Context context) {
		return getLewaUiVersion(context) == 6;
	}

	public static boolean isLewaUi(Context context) {
		return (isV5Ui(context) || isV6Ui(context));
	}
    
    public static int resolveAttribute(Context context, int attrId) {
        TypedValue typedValue = (TypedValue)sTypedValuePool.acquire();
        int ret = -1;
        if(context.getTheme().resolveAttribute(attrId, typedValue, true))
            ret = typedValue.resourceId;
        sTypedValuePool.release(typedValue);
        return ret;
    }
    
    public static int dip2px(Context context, float dpValue) { 
        final float scale = context.getResources().getDisplayMetrics().density; 
        return (int) (dpValue * scale + 0.5f); 
     }
    
    public static boolean isSpinnerV5Style( Context context,AttributeSet attrs){
    	TypedArray a = context.obtainStyledAttributes(attrs,  
                com.lewa.internal.R.styleable.V5spinner);  
    	return 	a.getBoolean(com.lewa.internal.R.styleable.V5spinner_isV5Spinner,false);
    }
    
    static int getIndexOfStates(int state)
    {
        int i = 0; 
        Integer value;
        if (state != 0) { 
            value = (Integer)sStateAttributeIndexes.get(state);
            if (value == null) { 
                if (sStateAttributeIndexes.size() >= 32) {
                    throw new IllegalArgumentException("State attribute cannot exceed 32!");
                }

                i = 1 << sStateAttributeIndexes.size(); 
                sStateAttributeIndexes.put(state, Integer.valueOf(i));
            } else {
                return value.intValue();
            }
        }
        return i;
    }

    static int getIndexOfStates(int states[])
    {
        int j = 0;
        if(states != null) {
            for(int i = 0; i < states.length; i++) {
                j |= getIndexOfStates(states[i]);
            }
        }
        return j;
    }
    
    public static int[] getViewStates(int states[], int additional)
    {
        int newStates[] = states;
        if(additional != 0)
        {
            int index = getIndexOfStates(states) | getIndexOfStates(additional);
            Object obj = sViewStates.get(index);
            newStates = (int[])obj;
            if(newStates == null) {
                int length = states != null ? states.length : 0;
                newStates = new int[length + 1];
                if(length != 0) {
                    System.arraycopy(states, 0, newStates, 0, states.length);
                }
                newStates[length] = additional;
                sViewStates.put(index, newStates);
            }
        }
        return newStates;
    }
}

