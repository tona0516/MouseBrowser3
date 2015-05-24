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
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
public class MainActivity extends FragmentActivity {
	public static CustomViewPager viewPager;
	public static DynamicFragmentPagerAdapter adapter;

	//Viewの位置を格納する変数
	private static int currentPosition = 0;
	private static int lastIndex = 0;

	//view命名用
	private static int count = 0;


	public static final String HOME = "https://www.google.co.jp/";
	private MainActivity main;

	private ArrayList<ArrayList<String>> urlList;
	private ArrayList<Integer> indexList;
	private SharedPreferences sp;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(null);
		setContentView(R.layout.activity_main);
		main = this;
		readHistoryList();
		sp = PreferenceManager.getDefaultSharedPreferences(this);
		viewPager = (CustomViewPager) findViewById(R.id.pager);
		viewPager.setOffscreenPageLimit(5);
		adapter = new DynamicFragmentPagerAdapter(getSupportFragmentManager());
		if (urlList.isEmpty()) {
			adapter.add("page" + (count++), new CustomWebViewFragment(HOME));
			addPagetoList(HOME);
		} else {
			for (int i = 0; i < urlList.size(); i++) {
				CustomWebViewFragment f = new CustomWebViewFragment(urlList.get(i).get(indexList.get(i)));
				adapter.add("page" + (count++), f);
			}
			lastIndex = sp.getInt("last", 0);
		}
		viewPager.setAdapter(adapter);
		viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				Log.d("position", "" + position);
				super.onPageSelected(position);
				currentPosition = position;
				sp.edit().putInt("last", position).commit(); //こう書かないとcommitされない
				CustomWebViewFragment f = adapter.get(currentPosition);
				if (f.getWebView() != null && !f.getWebView().isFocused()) {
					f.getWebView().requestFocus();
				}
			}
		});
		viewPager.setCurrentItem(lastIndex);
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.reload) {
			adapter.get(currentPosition).getWebView().reload();
		} else if (id == R.id.general_settings) {
			startActivity(new Intent(getApplicationContext(), GeneralPref.class));
		} else if (id == R.id.cursor_settings) {
			startActivity(new Intent(getApplicationContext(), Pref.class));
		} else if (id == R.id.create) {
			CustomWebViewFragment f = adapter.get(currentPosition);
			f.turnOffCursor();
			createFragment(HOME);
			addPagetoList(HOME);
		} else if (id == R.id.remove) {
			removeFragment();
			removePagetoList();
		} else if (id == R.id.url_bar) {
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
		}
		return super.onOptionsItemSelected(item);
	}

	public static void createFragment(String url) {
		if (url == null) {
			adapter.add("page" + (count++), new CustomWebViewFragment(null));
		} else {
			adapter.add("page" + (count++), new CustomWebViewFragment(url));
		}
		adapter.notifyDataSetChanged();
		viewPager.setCurrentItem(adapter.getCount() - 1);
	}

	public static void removeFragment() {
		if (adapter.getCount() != 1) {
			adapter.remove(currentPosition);
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onBackPressed() {
		ArrayList<String> list = urlList.get(currentPosition);
		CustomWebViewFragment f = adapter.get(currentPosition);

		int i = indexList.get(currentPosition);
		if (i - 1 > -1) {
			f.getWebView().loadUrl(list.get(i - 1));
			indexList.set(currentPosition, i - 1);
			f.setIsReturn(true);
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
	private void addPagetoList(String url) {
		ArrayList<String> list = new ArrayList<String>();
		list.add(url);
		urlList.add(list);
		indexList.add(0);
		writeHistoryList();
	}

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

	private void removePagetoList() {
		urlList.remove(currentPosition);
		indexList.remove(currentPosition);
		writeHistoryList();
	}

	private void writeHistoryList() {
		Log.d("indexList", indexList + "");
		ObjectOutputStream oos = null;
		FileOutputStream fos = null;
		ObjectOutputStream oos2 = null;
		FileOutputStream fos2 = null;
		try {
			fos = openFileOutput("url.obj", MODE_PRIVATE);
			oos = new ObjectOutputStream(fos);
			fos2 = openFileOutput("index.obj", MODE_PRIVATE);
			oos2 = new ObjectOutputStream(fos2);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			oos.writeObject(urlList);
			oos2.writeObject(indexList);
			fos.flush();
			fos.close();
			oos.flush();
			oos.close();
			fos2.flush();
			fos2.close();
			oos2.flush();
			oos2.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void readHistoryList() {
		ObjectInputStream ois = null;
		FileInputStream fis = null;
		ObjectInputStream ois2 = null;
		FileInputStream fis2 = null;
		try {
			fis = openFileInput("url.obj");
			ois = new ObjectInputStream(fis);
			fis2 = openFileInput("index.obj");
			ois2 = new ObjectInputStream(fis2);
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
				fis.close();
				ois.close();
				fis2.close();
				ois2.close();
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
	}
}
