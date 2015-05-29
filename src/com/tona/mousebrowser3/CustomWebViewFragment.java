package com.tona.mousebrowser3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.TextSize;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ToggleButton;
public class CustomWebViewFragment extends Fragment {

	// component
	private WebView mWebView;
	private ProgressBar mProgressBar;
	private RelativeLayout mLayout;
	private ImageView ivMouseCursor;
	private ToggleButton btnEnable;
	private Button btnMenu;
	private View mViewLeft, mViewRight, mViewBottom, mViewPointer;
	private EditText editForm;

	private boolean isCursorEnabled = false;
	private boolean isScrollMode = false;
	private boolean isNoShowCursorRange = false;
	private boolean isShowClickLocation = false;
	private boolean isEnableJavaScript = true;
	private boolean isEnableCache = true;
	private boolean isFirstView = true;
	private boolean isHistoryTransfer = false;

	private SharedPreferences pref;
	private Cursor cursor;
	private float downX, downY;
	private float upX, upY;

	private final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;

	private String mUrl = null;
	private Bundle mWebViewBundle;

	public CustomWebViewFragment(String url) {
		this.mUrl = url;
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment0, null);
		pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

		initComponent(v);
		initWebView(v);
		return v;
	}

	private void initComponent(View v) {
		mLayout = (RelativeLayout) v.findViewById(R.id.root_layout);
		mViewLeft = (View) v.findViewById(R.id.view_left);
		mViewRight = (View) v.findViewById(R.id.view_right);
		mViewBottom = (View) v.findViewById(R.id.view_bottom);
		mProgressBar = (ProgressBar) v.findViewById(R.id.progress_bar);
		btnEnable = (ToggleButton) v.findViewById(R.id.btn_enable);
		mViewPointer = new PointerView(getActivity());
		btnMenu = (Button) v.findViewById(R.id.btn_menu);
		btnMenu.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getActivity().openOptionsMenu();
			}
		});
		editForm = (EditText) v.findViewById(R.id.form);
		editForm.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				// EnterKeyが押されたかを判定
				if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
					// ソフトキーボードを閉じる
					InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
					inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
					// 検索処理的
					String str = editForm.getText().toString();
					if (str.startsWith("http://") || str.startsWith("https://")) {
						mWebView.loadUrl(editForm.getText().toString());
					} else {
						String searchWord = "http://www.google.co.jp/search?q=" + str.replaceAll(" ", "+");
						mWebView.loadUrl(searchWord);
					}
					return true;
				}
				return false;
			}
		});
		editForm.setOnFocusChangeListener(new OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (!hasFocus) {
					editForm.setVisibility(View.GONE);
				}
			}
		});
		mLayout.addView(mViewPointer);

		btnEnable.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				switchCursorRnable();
			}
		});
	}

	private void clickByCursor() {
		mViewPointer.invalidate();
		mWebView.setOnTouchListener(null);
		MotionEvent ev = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, cursor.getX(), cursor.getY(), 0);
		mLayout.dispatchTouchEvent(ev);
		ev = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, cursor.getX(), cursor.getY(), 0);
		mLayout.dispatchTouchEvent(ev);
		mWebView.setOnTouchListener(new myOnSetTouchListener());
	}

	private void switchCursorRnable() {
		if (!isCursorEnabled) {
			turnOnCursor();
		} else {
			turnOffCursor();
		}
	}

	public void turnOnCursor() {
		mViewPointer.invalidate();
		mWebView.setOnTouchListener(new myOnSetTouchListener());
		isCursorEnabled = true;
		btnEnable.setText("ON");
		createCursorImage();
		switchViewCursorRange();
		// onSearchRequested();
		MainActivity.viewPager.setDisable(true);
	}

	public void turnOffCursor() {
		mViewPointer.invalidate();
		mWebView.setOnTouchListener(null);
		isCursorEnabled = false;
		btnEnable.setText("OFF");
		mLayout.removeView(ivMouseCursor);
		switchViewCursorRange();
		MainActivity.viewPager.setDisable(false);
	}

	private void initWebView(View v) {
		mWebView = (WebView) v.findViewById(R.id.webview);
		WebSettings settings = mWebView.getSettings();
		settings.setUseWideViewPort(true);

		// マルチタッチズームの有効
		settings.setBuiltInZoomControls(true);
		settings.setSupportZoom(true);
		try {
			Field mWebViewField = settings.getClass().getDeclaredField("mBuiltInZoomControls");
			mWebViewField.setAccessible(true);
			mWebViewField.set(settings, false);
		} catch (Exception e) {
			e.printStackTrace();
			settings.setBuiltInZoomControls(false);
		}

		mWebView.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				super.onPageStarted(view, url, favicon);
				editForm.setText(url);
				if (isFirstView) {
					isFirstView = false;
				} else if (isHistoryTransfer) {
					isHistoryTransfer = false;
				} else {
					((MainActivity) getActivity()).setPagetoList(url);
				}
			}
		});
		mWebView.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView view, int newProgress) {
				super.onProgressChanged(view, newProgress);
				mProgressBar.setVisibility(View.VISIBLE);
				mProgressBar.setProgress(newProgress);
				if (newProgress == 100) {
					mProgressBar.setProgress(0);
					mProgressBar.setVisibility(View.INVISIBLE);
				}
			}
		});
		mWebView.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				// 長押しした箇所の情報を取得
				final HitTestResult htr = mWebView.getHitTestResult();
				switch (htr.getType()) {
					case HitTestResult.IMAGE_TYPE :
						AlertDialog.Builder alertDlg = new AlertDialog.Builder(getActivity());
						alertDlg.setTitle("画像");
						alertDlg.setMessage("保存しますか？");
						alertDlg.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								AsyncTask<String, Void, Boolean> task = new AsyncTask<String, Void, Boolean>() {
									@Override
									protected Boolean doInBackground(String... params) {
										return onDownload(htr.getExtra());
									}
									protected boolean onDownload(String url) {
										try {
											File root = new File(MainActivity.ROOTPATH);
											if (!root.exists()) {
												root.mkdir();
											}

											// 現在時刻をファイル名とする
											Date mDate = new Date();
											SimpleDateFormat fileNameDate = new SimpleDateFormat("yyyyMMdd_HHmmss");
											String fileName = fileNameDate.format(mDate) + ".jpg";
											String AttachName = root.getAbsolutePath() + "/" + fileName;

											// 出力ストリーム
											FileOutputStream out = new FileOutputStream(AttachName);

											// 入力ストリームで画像を読み込む
											URL mUrl = new URL(url);
											InputStream istream = mUrl.openStream();

											// 読み込んだファイルをビットマップに変換
											Bitmap oBmp = BitmapFactory.decodeStream(istream);
											// 保存
											oBmp.compress(CompressFormat.JPEG, 100, out);
											// 終了処理
											out.flush();
											out.close();
											return true;
										} catch (Exception e) {
											e.printStackTrace();
										}
										return false;
									}

									protected void onPostExecute(Boolean result) {
										if (result)
											Toast.makeText(getActivity(), "保存しました", Toast.LENGTH_SHORT).show();
										else
											Toast.makeText(getActivity(), "失敗しました", Toast.LENGTH_SHORT).show();
									};
								};
								task.execute();

							}
						});
						alertDlg.setNegativeButton("No", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
							}
						});
						alertDlg.show();
						return true;
					case HitTestResult.SRC_IMAGE_ANCHOR_TYPE :
						createDialong(htr.getExtra());
						return true;
					case HitTestResult.SRC_ANCHOR_TYPE :
						createDialong(htr.getExtra());
						return true;
					default :
						break;
				}
				return false;
			}

			private void createDialong(final String url) {
				AlertDialog.Builder alertDlg = new AlertDialog.Builder(getActivity());
				alertDlg.setTitle("選択");
				alertDlg.setMessage("新しいタブで開きますか？");
				alertDlg.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						MainActivity.createFragment(url);
					}
				});
				alertDlg.setNegativeButton("No", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				});
				alertDlg.show();
			}
		});
		if (mUrl != null) {
			mWebView.loadUrl(mUrl);
		} else {
			mWebView.loadUrl(pref.getString("homepage", MainActivity.DEFAULT_HOME));
		}
	}

	class myOnSetTouchListener implements View.OnTouchListener {
		@Override
		public boolean onTouch(View view, MotionEvent event) {

			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN :
					float x = event.getX();
					float y = event.getY();
					if (!isCursorOperationRange(x, y)) {
						isScrollMode = true;
						return false;
					}
					downX = event.getX();
					downY = event.getY();
					cursor.setDownX(cursor.getX());
					cursor.setDownY(cursor.getY());
					break;
				case MotionEvent.ACTION_MOVE :
					if (isScrollMode)
						return false;
					float newX = (cursor.getDownX() - (downX - event.getX()) * cursor.getV());
					float newY = (cursor.getDownY() - (downY - event.getY()) * cursor.getV());
					cursor.setX(newX);
					cursor.setY(newY);
					ivMouseCursor.setX(newX);
					ivMouseCursor.setY(newY);
					int disX = cursor.getDisplaySize().x;
					int disY = cursor.getDisplaySize().y;
					if (newX > cursor.getDisplaySize().x) {
						cursor.setX(disX);
						ivMouseCursor.setX(disX);
						cursor.setDownX(disX);
						downX = event.getX();
					}
					if (newX < 0) {
						cursor.setX(0);
						ivMouseCursor.setX(0);
						cursor.setDownX(0);
						downX = event.getX();
					}
					if (newY > disY) {
						cursor.setY(disY);
						ivMouseCursor.setY(disY);
						cursor.setDownY(disY);
						downY = event.getY();
					}
					if (newY < 0) {
						cursor.setY(0);
						ivMouseCursor.setY(0);
						cursor.setDownY(0);
						downY = event.getY();
					}
					break;
				case MotionEvent.ACTION_UP :
					isScrollMode = false;
					upX = event.getX();
					upY = event.getY();
					float absX = Math.abs(downX - upX);
					float absY = Math.abs(downY - upY);
					Log.d("ABS", absX + "," + absY);
					if (absX < 10 && absY < 10) {
						clickByCursor();
						return true;
					}
					return false;
				default :
					break;
			}
			return true;
		}
	}

	private boolean isCursorOperationRange(float x, float y) {
		Log.d("point", "(" + x + "," + y + ")");
		if (cursor.getOperationRange().equals("right")) {
			if (x > cursor.getDisplaySize().x / 2 && x < cursor.getDisplaySize().x) {
				return true;
			}
		}
		if (cursor.getOperationRange().equals("left")) {
			if (x > 0 && x < cursor.getDisplaySize().x / 2) {
				return true;
			}
		}
		if (cursor.getOperationRange().equals("bottom")) {
			if (y > cursor.getDisplaySize().y * 2 / 3 - (mProgressBar.getHeight() + editForm.getHeight()) && y < cursor.getDisplaySize().y) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d("LifeCycle", "onResume");
		Point p = getWindowSize();
		cursor = new Cursor(p.x, p.y);
		mViewBottom.setY(cursor.getDisplaySize().y * 2 / 3);
		readPreference();
		switchViewCursorRange();
		createCursorImage();
	}

	private Point getWindowSize() {
		WindowManager wm = (WindowManager) getActivity().getSystemService(getActivity().WINDOW_SERVICE);
		Display disp = wm.getDefaultDisplay();
		Point size = new Point();
		disp.getSize(size);
		Log.d("size", size.x + "," + size.y);
		return size;
	}

	private void readPreference() {
		cursor.setV(Float.parseFloat(pref.getString("velocity", "1.0")));
		cursor.setSizeRate(Float.parseFloat(pref.getString("size_rate", "1.0")));
		cursor.setOperationRange(pref.getString("range", "bottom"));
		isNoShowCursorRange = pref.getBoolean("view_cursor_range", false);
		isShowClickLocation = pref.getBoolean("click_location", false);
		isEnableJavaScript = pref.getBoolean("enable_javascript", true);
		mWebView.getSettings().setJavaScriptEnabled(isEnableJavaScript);
		isEnableCache = pref.getBoolean("enable_cache", true);
		mWebView.getSettings().setAppCacheEnabled(isEnableCache);
		if (isEnableCache) {
			mWebView.getSettings().setAppCachePath(MainActivity.ROOTPATH + "cache/");
		} else {
			mWebView.clearCache(true);
		}
		int textsize = Integer.parseInt(pref.getString("textsize", "100"));
		mWebView.getSettings().setTextZoom(textsize);
	}
	@Override
	public void onStop() {
		super.onStop();
		mLayout.removeView(ivMouseCursor);
	}

	private void createCursorImage() {
		ivMouseCursor = new ImageView(getActivity());
		Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.cursor);
		Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, (int) cursor.getWidth(), (int) cursor.getHeight(), false); // 13:16で調整
		ivMouseCursor.setImageBitmap(bmp2);
		ivMouseCursor.setLayoutParams(new LayoutParams(WC, WC));
		ivMouseCursor.setX(cursor.getX());
		ivMouseCursor.setY(cursor.getY());
		if (isCursorEnabled)
			ivMouseCursor.setVisibility(View.VISIBLE);
		else
			ivMouseCursor.setVisibility(View.INVISIBLE);
		mLayout.addView(ivMouseCursor);
	}

	private void switchViewCursorRange() {
		if (isCursorEnabled && !isNoShowCursorRange) {
			Log.d("range", cursor.getOperationRange());
			if (cursor.getOperationRange().equals("right")) {
				mViewRight.setVisibility(View.VISIBLE);
				mViewLeft.setVisibility(View.INVISIBLE);
				mViewBottom.setVisibility(View.INVISIBLE);
			} else if (cursor.getOperationRange().equals("left")) {
				mViewLeft.setVisibility(View.VISIBLE);
				mViewRight.setVisibility(View.INVISIBLE);
				mViewBottom.setVisibility(View.INVISIBLE);
			} else if (cursor.getOperationRange().equals("bottom")) {
				mViewBottom.setVisibility(View.VISIBLE);
				mViewLeft.setVisibility(View.INVISIBLE);
				mViewRight.setVisibility(View.INVISIBLE);
			}
		} else {
			mViewLeft.setVisibility(View.INVISIBLE);
			mViewRight.setVisibility(View.INVISIBLE);
			mViewBottom.setVisibility(View.INVISIBLE);
		}
	}

	/**
	 * クリック位置を表示するView
	 *
	 * @author meem
	 *
	 */
	private class PointerView extends View {
		Paint paint;

		public PointerView(Context context) {
			super(context);
			paint = new Paint();
			paint.setColor(Color.RED);
			paint.setStrokeWidth(3);
		}

		@Override
		protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			if (isCursorEnabled && isShowClickLocation) {
				canvas.drawLine(0, cursor.getY(), cursor.getDisplaySize().x, cursor.getY(), paint);
				canvas.drawLine(cursor.getX(), 0, cursor.getX(), cursor.getDisplaySize().y, paint);
			}
		}
	}

	@Override
	public void setMenuVisibility(boolean menuVisible) {
		super.setMenuVisibility(menuVisible);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		MainActivity.viewPager.setDisable(false);
	}

	public WebView getWebView() {
		return mWebView;
	}

	public EditText getEditForm() {
		return editForm;
	}
	public void setIsHistoryTransfer(boolean b) {
		isHistoryTransfer = b;
	}
}