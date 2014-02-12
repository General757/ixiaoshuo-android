package com.vincestyling.ixiaoshuo.view.finder;

import android.graphics.Color;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import com.vincestyling.ixiaoshuo.R;
import com.vincestyling.ixiaoshuo.pojo.Book;
import com.vincestyling.ixiaoshuo.reader.MainActivity;
import com.vincestyling.ixiaoshuo.ui.FinderMenuGridView;
import com.vincestyling.ixiaoshuo.ui.ScrollLayout;
import com.vincestyling.ixiaoshuo.view.ViewBuilder;

public class FinderView extends ViewBuilder {
	private ScrollLayout mLotMainContent;
	private FinderMenuGridView mFinderMenuView;
	private Button mBtnTextsBook, mBtnVoicesBook;

	public FinderView(MainActivity activity, OnShowListener onShowListener) {
		mShowListener = onShowListener;
		mViewId = R.id.lotBookFinder;
		setActivity(activity);
	}

	@Override
	protected void build() {
		mView = getActivity().getLayoutInflater().inflate(R.layout.finder, null);
	}

	@Override
	public void init() {
		mLotMainContent = (ScrollLayout) findViewById(R.id.lotBookFinderContent);

		mFinderMenuView = (FinderMenuGridView) findViewById(R.id.finderMenuView);
		mFinderMenuView.setFinderView(this);

		mBtnTextsBook = (Button) findViewById(R.id.btnTextsBook);
		mBtnVoicesBook = (Button) findViewById(R.id.btnVoicesBook);

		mBtnTextsBook.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onBtnClick(mBtnTextsBook);
			}
		});

		mBtnVoicesBook.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onBtnClick(mBtnVoicesBook);
			}
		});

		onBtnClick(mBtnTextsBook);
	}

	private void onBtnClick(Button btnView) {
		highlightBtn(btnView);
		mLotMainContent.removeAllViews();
		showView(mFinderMenuView.buildViewBuilder(mFinderMenuView.getSelectedItemId()));
	}

	private void highlightBtn(Button btnView) {
		if (btnView == mBtnTextsBook) {
			mFinderMenuView.setBookType(Book.TYPE_TEXT);
			turnOnButton(mBtnTextsBook);
		} else {
			turnOffButton(mBtnTextsBook);
		}

		if (btnView == mBtnVoicesBook) {
			mFinderMenuView.setBookType(Book.TYPE_VOICE);
			turnOnButton(mBtnVoicesBook);
		} else {
			turnOffButton(mBtnVoicesBook);
		}
	}

	private void turnOnButton(Button btnView) {
		btnView.setBackgroundResource(R.drawable.title_item_selected);
		btnView.setTextColor(Color.parseColor("#fefffc"));
	}

	private void turnOffButton(Button btnView) {
		btnView.setBackgroundColor(Color.TRANSPARENT);
		btnView.setTextColor(Color.parseColor("#b1d596"));
	}

	public void showView(ViewBuilder builder) {
		mLotMainContent.showView(builder);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return mLotMainContent.onKeyDown(keyCode, event);
	}

}
