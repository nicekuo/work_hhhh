package nice.com.jzs.camera;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ImageView;

import nice.com.jzs.R;


/**
 * Created by sreay on 14-9-3.
 */
public class FixHeightRadioImageView extends ImageView {
	private float radio = 1.0f;
	private int width = 0;
	private int height = 0;

	public FixHeightRadioImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs, defStyle);
	}

	public FixHeightRadioImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs, 0);
	}

	public FixHeightRadioImageView(Context context) {
		super(context);
		init(context, null, 0);
	}

	private void init(Context context, AttributeSet attrs, int defStyle) {
		if (attrs != null) {
			TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.FixHeightRadioImageView);
			radio = array.getFloat(R.styleable.FixHeightRadioImageView_h_radio, 1.0f);
			array.recycle();
		}
	}

	//radio = width/height
	public void setRadio(float radio) {
		this.radio = radio;
        requestLayout();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		width = widthSize;
		height = (int) (width * radio);
		setMeasuredDimension(width, height);
	}
}
