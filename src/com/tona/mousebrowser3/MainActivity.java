package com.tona.mousebrowser3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;
public class MainActivity extends FragmentActivity {
	public static CustomViewPager viewPager;
	public static DynamicFragmentPagerAdapter adapter;

	// Viewの位置を格納する変数
	private static int currentPosition = 0;
	private static int lastIndex = 0;

	// view命名用
	private static int count = 0;

	// デフォルトのHP
	public static final String DEFAULT_HOME = "https://www.google.co.jp/";

	// 画像・キャッシュを保存する際のパス
	public static final String ROOTPATH = Environment.getExternalStorageDirectory().getPath() + "/MouseBrowser/";

	// 他クラスで使用する際のMainActivity変数
	private MainActivity main;

	// タブの状態保存のURL＆インデックスリスト
	private ArrayList<ArrayList<String>> urlList;
	private ArrayList<Integer> indexList;
	private ArrayList<Pair<Integer, Integer>> scrollList;

	// ユーザ設定保存変数
	private SharedPreferences sp;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d("LifeCycle", "OnCreate");
		super.onCreate(null);
		setContentView(R.layout.activity_main);
		main = this;
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		initializeAdapter();
		initializeViewPager();
	}

	/**
	 * 閲覧履歴があればその状態を読み込み、Adapterに反映する
	 */
	private void initializeAdapter() {
		readHistoryList();
		adapter = new DynamicFragmentPagerAdapter(getSupportFragmentManager());
		if (urlList.isEmpty()) {
			adapter.add("page" + (count++), new CustomWebViewFragment(sp.getString("homepage", DEFAULT_HOME)));
			addPagetoList(sp.getString("hompage", DEFAULT_HOME));
			scrollList.add(new Pair<Integer, Integer>(0, 0));
		} else {
			for (int i = 0; i < urlList.size(); i++) {
				CustomWebViewFragment f = new CustomWebViewFragment(urlList.get(i).get(indexList.get(i)));
				adapter.add("page" + (count++), f);
			}
			lastIndex = sp.getInt("last", 0);
		}
	}
	/**
	 * AdapterをViwePagerに反映
	 */
	private void initializeViewPager() {
		viewPager = (CustomViewPager) findViewById(R.id.pager);
		viewPager.setAdapter(adapter);
		viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				Log.d("position", "" + position);
				super.onPageSelected(position);
//				if (currentPosition < adapter.getCount()) {
//					WebView v = adapter.get(currentPosition).getWebView();
//					setScrollList(currentPosition, v.getScrollX(), v.getScrollY());
//				}
//				restoreScrollPosition(position);
				currentPosition = position;
				sp.edit().putInt("last", position).commit(); // こう書かないとcommitされない
				CustomWebViewFragment f = adapter.get(currentPosition);
				if (f.getWebView() != null && !f.getWebView().isFocused()) {
					f.getWebView().requestFocus();
				}
			}
		});
		viewPager.setCurrentItem(lastIndex);
		currentPosition = lastIndex;
	}
	/**
	 * メニューの作成
	 *
	 * @param menu
	 * @return
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	/**
	 * メニューがクリックされたときの処理の振り分け
	 *
	 * @param item
	 * @return
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
			case R.id.bookmark :
				Intent intent2 = new Intent(Intent.ACTION_CREATE_SHORTCUT);
				startActivityForResult(intent2, 0);
				break;
			case R.id.bookmark_add :
				Intent intent = new Intent(Intent.ACTION_INSERT, android.provider.Browser.BOOKMARKS_URI);
				intent.putExtra("title", adapter.get(currentPosition).getWebView().getTitle());
				intent.putExtra("url", adapter.get(currentPosition).getWebView().getUrl());
				startActivity(intent);
				break;
			case R.id.next :
				ArrayList<String> list = urlList.get(currentPosition);
				CustomWebViewFragment f2 = adapter.get(currentPosition);
				int i = indexList.get(currentPosition);

				if (i + 1 < list.size()) {
					f2.getWebView().loadUrl(list.get(i + 1));
					indexList.set(currentPosition, i + 1);
					f2.setIsHistoryTransfer(true);
				}
				break;
			case R.id.reload :
				adapter.get(currentPosition).getWebView().reload();
				break;
			case R.id.general_settings :
				startActivity(new Intent(getApplicationContext(), GeneralPref.class));
				break;
			case R.id.cursor_settings :
				startActivity(new Intent(getApplicationContext(), Pref.class));
				break;
			case R.id.create :
				CustomWebViewFragment f = adapter.get(currentPosition);
				f.turnOffCursor();
				createFragment(sp.getString("homepage", DEFAULT_HOME));
				addPagetoList(sp.getString("homepage", DEFAULT_HOME));
				break;
			case R.id.remove :
				removeFragment();
				removePagetoList();
				break;
			case R.id.url_bar :
				final EditText e = adapter.get(currentPosition).getEditForm();
				e.setVisibility(View.VISIBLE);
				e.requestFocus();
				e.setSelection(0, e.getText().length());
				// 遅らせてフォーカスがセットされるのを待つ
				main.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Timer t = new Timer();
						t.schedule(new TimerTask() {
							@Override
							public void run() {
								InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
								inputMethodManager.showSoftInput(e, InputMethodManager.SHOW_IMPLICIT);
							}
						}, 200);
					}
				});
				break;
			default :
				break;
		}
		return super.onOptionsItemSelected(item);
	}
	/**
	 * ブックマークを呼び出して、選択した項目のURLを取得し表示する
	 *
	 * @param requestCode
	 * @param resultCode
	 * @param data
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
			String uri = intent.toURI();
			if (uri.startsWith("http://") || uri.startsWith("https://")) {
				CustomWebViewFragment f = new CustomWebViewFragment(uri);
				adapter.add("page" + (count++), f);
				adapter.notifyDataSetChanged();
				viewPager.setCurrentItem(adapter.getCount() - 1);
				addPagetoList(uri);
			} else {
				Toast.makeText(main, "不正なURLです", Toast.LENGTH_SHORT).show();
			}
		}
	}

	/**
	 * 新しいタブを作成する
	 *
	 * @param url
	 */
	public static void createFragment(String url) {
		if (url == null) {
			adapter.add("page" + (count++), new CustomWebViewFragment(null));
		} else {
			adapter.add("page" + (count++), new CustomWebViewFragment(url));
		}
		adapter.notifyDataSetChanged();
		viewPager.setCurrentItem(adapter.getCount() - 1);
		viewPager.setOffscreenPageLimit(adapter.getCount());
	}

	/**
	 * 現在のタブを削除する
	 */
	public static void removeFragment() {
		if (adapter.getCount() != 1) {
			adapter.remove(currentPosition);
			adapter.notifyDataSetChanged();
			viewPager.setOffscreenPageLimit(adapter.getCount());
		}
	}

	/**
	 * バックボタンが押下された時の挙動
	 */
	@Override
	public void onBackPressed() {
		ArrayList<String> list = urlList.get(currentPosition);
		CustomWebViewFragment f = adapter.get(currentPosition);

		int i = indexList.get(currentPosition);
		if (i - 1 > -1) {
			f.getWebView().loadUrl(list.get(i - 1));
			indexList.set(currentPosition, i - 1);
			f.setIsHistoryTransfer(true);
		} else {
			removeFragment();
			removePagetoList();
			if (adapter.getCount() == 0) {
				new File("url.obj").delete();
				new File("index.obj").delete();
			}
			finish();
		}
		writeHistoryList();
		Log.d("indexList", indexList + "");
		return;
	}

	/**
	 * タブが追加されたら履歴に保存する
	 *
	 * @param url
	 */
	private void addPagetoList(String url) {
		ArrayList<String> list = new ArrayList<String>();
		list.add(url);
		urlList.add(list);
		indexList.add(0);
		scrollList.add(new Pair<Integer, Integer>(0, 0));
		writeHistoryList();
	}

	public void restoreScrollPosition(int index) {
		Pair<Integer, Integer> p = scrollList.get(index);
		adapter.get(index).getWebView().scrollTo(p.first, p.second);
	}

	public void setScrollList(int position, int x, int y) {
		scrollList.set(position, new Pair<Integer, Integer>(x, y));
	}
	/**
	 * 表示が変更されたら履歴に追加する
	 *
	 * @param url
	 */
	public void setPagetoList(String url) {
		ArrayList<String> list = urlList.get(currentPosition);
		int i = indexList.get(currentPosition);
		if (i != list.size() - 1) { // 現在のページが最新なら
			for (int j = i + 1; j < list.size(); j++) {
				list.remove(j); // それより前にあったページの履歴を消す
			}
		}
		list.add(url);
		indexList.set(currentPosition, i + 1);
		urlList.set(currentPosition, list);
		writeHistoryList();
	}

	/**
	 * タブが削除されたら履歴を消す
	 */
	private void removePagetoList() {
		urlList.remove(currentPosition);
		indexList.remove(currentPosition);
		scrollList.remove(currentPosition);
		writeHistoryList();
	}

	/**
	 * ファイルに履歴を書き込む
	 */
	private void writeHistoryList() {
		Log.d("indexList", indexList + "");
		ObjectOutputStream oos = null;
		FileOutputStream fos = null;
		ObjectOutputStream oos2 = null;
		FileOutputStream fos2 = null;
		ObjectOutputStream oos3 = null;
		FileOutputStream fos3 = null;
		try {
			fos = openFileOutput("url.obj", MODE_PRIVATE);
			oos = new ObjectOutputStream(fos);
			fos2 = openFileOutput("index.obj", MODE_PRIVATE);
			oos2 = new ObjectOutputStream(fos2);
			fos3 = openFileOutput("scroll.obj", MODE_PRIVATE);
			oos3 = new ObjectOutputStream(fos3);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			oos.writeObject(urlList);
			oos2.writeObject(indexList);
			oos3.writeObject(scrollList);
			fos.flush();
			fos.close();
			oos.flush();
			oos.close();
			fos2.flush();
			fos2.close();
			oos2.flush();
			oos2.close();
			fos3.flush();
			fos3.close();
			oos3.flush();
			oos3.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 起動時に履歴を読み込む
	 */
	private void readHistoryList() {
		ObjectInputStream ois = null;
		FileInputStream fis = null;
		ObjectInputStream ois2 = null;
		FileInputStream fis2 = null;
		ObjectInputStream ois3 = null;
		FileInputStream fis3 = null;
		try {
			fis = openFileInput("url.obj");
			ois = new ObjectInputStream(fis);
			fis2 = openFileInput("index.obj");
			ois2 = new ObjectInputStream(fis2);
			fis3 = openFileInput("scroll.obj");
			ois3 = new ObjectInputStream(fis3);
		} catch (StreamCorruptedException e1) {
			e1.printStackTrace();
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (ois != null && ois2 != null) {
			try {
				urlList = (ArrayList<ArrayList<String>>) ois.readObject();
				indexList = (ArrayList<Integer>) ois2.readObject();
				scrollList = (ArrayList<Pair<Integer, Integer>>) ois3.readObject();
				fis.close();
				ois.close();
				fis2.close();
				ois2.close();
				fis3.close();
				ois3.close();
			} catch (OptionalDataException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (urlList == null)
			urlList = new ArrayList<ArrayList<String>>();
		if (indexList == null)
			indexList = new ArrayList<Integer>();
		if (scrollList == null)
			scrollList = new ArrayList<Pair<Integer, Integer>>();
	}
}
