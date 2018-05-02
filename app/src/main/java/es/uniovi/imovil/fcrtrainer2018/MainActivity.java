/*

Copyright 2014 Profesores y alumnos de la asignatura Informática Móvil de la EPI de Gijón

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/

package es.uniovi.imovil.fcrtrainer2018;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.AchievementsClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;

import es.uniovi.imovil.fcrtrainer2018.SectionedDrawerAdapter.Group;

public class MainActivity extends AppCompatActivity implements
		ListView.OnItemClickListener, BaseExerciseFragment.Listener {

	/**
	 * Nombre del fichero de preferencias.
	 */
	private static final String PREFERENCES = "preferences";
	/**
	 * Preferencia donde se almacena el último ejercicio accedido.
	 */
	private static final String LAST_EXERCISE = "last_exercise";
	/**
	 * Preferencia que indica que el usuario sabe manejar el drawer. La guía de
	 * Android recomienda mostrar el Drawer abierto hasta que el usuario lo haya
	 * desplegado al menos una vez.
	 */
	private static final String USER_LEARNED_DRAWER = "user_learned_drawer";

	private DrawerLayout mDrawerLayout;
	private ActionBarDrawerToggle mDrawerToggle;
	private ListView mDrawerList;
	private CharSequence mDrawerTitle;
	private CharSequence mTitle;
	private int mExerciseResIndex;
	private boolean mUserLearnedDrawer;

	// Google Games
	private AchievementsClient mAchievementsClient = null;
	private GoogleSignInAccount mSignedInAccount = null;
	private static final String TAG = "FCRTRAINER";
	private static final int RC_UNUSED = 5001;
	// Request code used to invoke sign in user interactions.
	private static final int RC_SIGN_IN = 9001;
	// Client used to sign in with Google APIs
	private GoogleSignInClient mGoogleSignInClient = null;
	private LeaderboardsClient mLeaderboardsClient = null;
	private PlayersClient mPlayersClient = null;
	// achievements and scores we're pending to push to the cloud
	// (waiting for the user to sign in, for instance)
	private final AccomplishmentsOutbox mOutbox = new AccomplishmentsOutbox();


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		mTitle = getTitle();
		mDrawerTitle = mTitle;
		boolean fromSavedInstanceState = false;

		// Create the client used to sign in to Google services.
		mGoogleSignInClient = GoogleSignIn.getClient(this,
				new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN).build());

		if (savedInstanceState != null) {
			// Recuperar el estado tras una interrupción
			mExerciseResIndex = savedInstanceState.getInt(LAST_EXERCISE);
			mUserLearnedDrawer = savedInstanceState
					.getBoolean(USER_LEARNED_DRAWER);
			fromSavedInstanceState = true;
		} else {
			// Restaurar el estado desde las preferencias
			SharedPreferences prefs = getSharedPreferences(PREFERENCES,
					Context.MODE_PRIVATE);
			mExerciseResIndex = prefs.getInt(LAST_EXERCISE, R.string.binary);
			mUserLearnedDrawer = prefs.getBoolean(USER_LEARNED_DRAWER, false);
		}

		// Cargo el fragmento con el contenido

		if (savedInstanceState == null)
			updateContentFragment();

		initializeDrawer(fromSavedInstanceState);
        startSignInIntent();
	}

	@Override
	public void onPause() {
		super.onPause();

		// Guardar las preferencias
		SharedPreferences prefs = getSharedPreferences(PREFERENCES,
				Context.MODE_PRIVATE);
		SharedPreferences.Editor prefsEditor = prefs.edit();
		prefsEditor.putInt(LAST_EXERCISE, mExerciseResIndex);
		prefsEditor.putBoolean(USER_LEARNED_DRAWER, mUserLearnedDrawer);
		prefsEditor.commit();
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);

		// Guardar el estado de la actividad
		savedInstanceState.putInt(LAST_EXERCISE, mExerciseResIndex);
		savedInstanceState.putBoolean(USER_LEARNED_DRAWER, mUserLearnedDrawer);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflar el menú		
		if (isDrawerOpen()) {
			// TODO: Si el Drawer está desplegado no deben mostrarse iconos de
			// acción
			getMenuInflater().inflate(R.menu.main, menu);
		} else {
			getMenuInflater().inflate(R.menu.main, menu);
		}
		return true;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		
		switch(item.getItemId()){
		case R.id.action_settings:
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
			break;
		case R.id.action_help:
			Intent goToHelp = new Intent(this, HelpActivity.class);
			startActivity(goToHelp);
			break;
		case R.id.action_achievements:
			onShowAchievementsRequested();
			break;
		case R.id.action_leaderboards:
			onShowLeaderboardsRequested();
			break;
		}
		
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		mDrawerToggle.syncState();
	}

	@Override
	public void setTitle(CharSequence title) {
		mTitle = title;
		getSupportActionBar().setTitle(mTitle);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		int newExerciseIndex = (Integer) parent.getItemAtPosition(position);
		if (newExerciseIndex != mExerciseResIndex) {
			// Cambiar el fragmento de contenido actual
			mExerciseResIndex = newExerciseIndex;
			updateContentFragment();
			mTitle = getString(mExerciseResIndex);
		}
		// Cerrar el Drawer
		mDrawerLayout.closeDrawer(mDrawerList);
	}

	public boolean isDrawerOpen() {
		return mDrawerLayout.isDrawerOpen(mDrawerList);
	}
	
	private void updateContentFragment() {
		Fragment fragment = FragmentFactory
				.createExercise(mExerciseResIndex);
		FragmentTransaction fragmentTransaction = getSupportFragmentManager()
				.beginTransaction();

		fragmentTransaction.replace(R.id.content_frame, fragment, "Hola");
		fragmentTransaction.commit();
	}

	private void initializeDrawer(boolean fromSavedState) {
		// Contenido organizado en secciones
		ArrayList<Group<String, Integer>> sections = createDrawerEntries();
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerList = (ListView) findViewById(R.id.left_drawer);
		mDrawerList.setAdapter(new SectionedDrawerAdapter(this,
				R.layout.drawer_list_item, R.layout.drawer_list_header,
				sections));

		// Listener
		mDrawerList.setOnItemClickListener(this);

		// Mostrar el icono del drawer
		final ActionBar actionBar = getSupportActionBar();
		mDrawerToggle = new ActionBarDrawerToggle(this, // Actividad que lo aloja
				mDrawerLayout, // El layout
				R.string.drawer_open, R.string.drawer_close) {

			// Se llama cuando el Drawer se acaba de cerrar
			public void onDrawerClosed(View view) {
				super.onDrawerClosed(view);
				actionBar.setTitle(mTitle);
				// Actualizar las acciones en el Action Bar
				supportInvalidateOptionsMenu();
			}

			// Se llama cuando el Drawer se acaba de abrir
			public void onDrawerOpened(View drawerView) {
				super.onDrawerOpened(drawerView);
				actionBar.setTitle(mDrawerTitle);
				// Actualizar las acciones en el Action Bar
				mUserLearnedDrawer = true;
				supportInvalidateOptionsMenu();
			}
		};

		// Si el usuario no ha desplegado alguna vez el Drawer
		mTitle = getString(mExerciseResIndex);
		if (!mUserLearnedDrawer && !fromSavedState) {
			mDrawerLayout.openDrawer(mDrawerList);
			actionBar.setTitle(mDrawerTitle);
		} else {			
			setTitle(mTitle);
		}

		mDrawerLayout.setDrawerListener(mDrawerToggle);

		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);
	}

	private ArrayList<Group<String, Integer>> createDrawerEntries() {
		ArrayList<Group<String, Integer>> sections = new ArrayList<Group<String,
				Integer>>();

		addSection(sections, R.string.codes, R.array.codes);
		addSection(sections, R.string.digital_systems, R.array.digital_systems);
		addSection(sections, R.string.networks, R.array.networks);
		addSection(sections, R.string.highscores, R.array.highscores);

		return sections;
	}

	private void addSection(ArrayList<Group<String, Integer>> sections,
			int sectionNameId, int childrenArrayId) {
		Group<String, Integer> group;
		group = new Group<String, Integer>(getString(sectionNameId));

		TypedArray array = getResources().obtainTypedArray(childrenArrayId);

		group.children = new Integer[array.length()];
		for (int i = 0; i < array.length(); i++) {
			int defaultId = 0;
			group.children[i] = array.getResourceId(i, defaultId);
		}
		
		array.recycle();
		
		sections.add(group);
	}

	@Override
	protected void onStart() {
		super.onStart();
		GoogleAnalytics.getInstance(this).reportActivityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		GoogleAnalytics.getInstance(this).reportActivityStop(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		signInSilently();
	}


	// GOOGLE GAMES STUFF
	private void signInSilently() {
		Log.d(TAG, "signInSilently()");

		mGoogleSignInClient.silentSignIn().addOnCompleteListener(this,
				new OnCompleteListener<GoogleSignInAccount>() {
					@Override
					public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
						if (task.isSuccessful()) {
							Log.d(TAG, "signInSilently(): success");
							onConnected(task.getResult());
						} else {
							Log.d(TAG, "signInSilently(): failure", task.getException());
							onDisconnected();
						}
					}
				});
	}

	private boolean isSignedIn() {
		return GoogleSignIn.getLastSignedInAccount(this) != null;
	}

	private void startSignInIntent() {
		startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);
	}

	private void signOut() {
		Log.d(TAG, "signOut()");

		if (!isSignedIn()) {
			Log.w(TAG, "signOut() called, but was not signed in!");
			return;
		}

		mGoogleSignInClient.signOut().addOnCompleteListener(this,
				new OnCompleteListener<Void>() {
					@Override
					public void onComplete(@NonNull Task<Void> task) {
						boolean successful = task.isSuccessful();
						Log.d(TAG, "signOut(): " + (successful ? "success" : "failed"));

						onDisconnected();
					}
				});
	}

	//@Override
	public void onShowAchievementsRequested() {
		mAchievementsClient.getAchievementsIntent()
				.addOnSuccessListener(new OnSuccessListener<Intent>() {
					@Override
					public void onSuccess(Intent intent) {
						startActivityForResult(intent, RC_UNUSED);
					}
				})
				.addOnFailureListener(new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						handleException(e, getString(R.string.achievements_exception));
					}
				});
	}
	//@Override
	public void onShowLeaderboardsRequested() {
		mLeaderboardsClient.getAllLeaderboardsIntent()
				.addOnSuccessListener(new OnSuccessListener<Intent>() {
					@Override
					public void onSuccess(Intent intent) {
						startActivityForResult(intent, RC_UNUSED);
					}
				})
				.addOnFailureListener(new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						handleException(e, getString(R.string.leaderboards_exception));
					}
				});
	}

	private void handleException(Exception e, String details) {
		int status = 0;

		if (e instanceof ApiException) {
			ApiException apiException = (ApiException) e;
			status = apiException.getStatusCode();
		}

		String message = getString(R.string.status_exception_error, details, status, e);

		new android.app.AlertDialog.Builder(MainActivity.this)
				.setMessage(message)
				.setNeutralButton(android.R.string.ok, null)
				.show();
	}


	public void onEnteredScore(int score, String level) {

		Log.d("DEBUG", String.valueOf(score));

		// check for achievements
		checkForAchievements(score);
		Log.d(getClass().getSimpleName(),"Achievements checked");

		// update leaderboards
		updateLeaderboards(score, level);
		Log.d(getClass().getSimpleName(),"Leaderboards updated");

		// push those accomplishments to the cloud, if signed in
		pushAccomplishments();
		Log.d(getClass().getSimpleName(),"Accomplishments pushed");
	}


	/**
	 * Check for achievements and unlock the appropriate ones.
	 *
	 * @param score the score the user got.
	 */
	public void checkForAchievements(int score) {
		// Check if each condition is met; if so, unlock the corresponding
		// achievement.
		// TODO: Reemplazar con nuestros logros comprobando sus condiciones

		/*
		if (requestedScore == 0) {
			mOutbox.mHumbleAchievement = true;
			achievementToast(getString(R.string.achievement_humble_toast_text));
		}
		if (finalScore == 1337) {
			mOutbox.mLeetAchievement = true;
			achievementToast(getString(R.string.achievement_leet_toast_text));
		}
		*/

		if (score >= 50) {
			mOutbox.mThatsAFiftyAchievement = true;
			achievementToast(getString(R.string.achievement_thatsa50_toast_text));
		}

		mOutbox.mBoredSteps++;

	}

	private void achievementToast(String achievement) {
		// Only show toast if not signed in. If signed in, the standard Google Play
		// toasts will appear, so we don't need to show our own.
		if (!isSignedIn()) {
			Toast.makeText(this, getString(R.string.achievement) + ": " + achievement,
					Toast.LENGTH_LONG).show();
		}
	}

	private void pushAccomplishments() {
		// Hacemos push del estado de todos los logros y puntuaciones
		// TODO: Reemplazar con nuestros logros
		if (!isSignedIn()) {
			// can't push to the cloud, try again later
			return;
		}
		if (mOutbox.mBoredSteps > 0) {
			mAchievementsClient.increment(getString(R.string.achievement_minihacker),
					mOutbox.mBoredSteps);
			mAchievementsClient.increment(getString(R.string.achievement_junior_hacker),
					mOutbox.mBoredSteps);
			mAchievementsClient.increment(getString(R.string.achievement_senior_hacker),
					mOutbox.mBoredSteps);
			mAchievementsClient.increment(getString(R.string.achievement_master_hacker),
					mOutbox.mBoredSteps);
			mOutbox.mBoredSteps = 0;
		}
		if(mOutbox.mThatsAFiftyAchievement){
			mAchievementsClient.unlock(getString(R.string.achievement_thats_a_50));
			mOutbox.mThatsAFiftyAchievement = false;
		}
		if (mOutbox.mMaxPointsAprendiz >= 0) {
			mLeaderboardsClient.submitScore(getString(R.string.leaderboard_aprendices),
					mOutbox.mMaxPointsAprendiz);
			mOutbox.mMaxPointsAprendiz = -1;
		}
		if (mOutbox.mMaxPointsIniciado >= 0) {
			mLeaderboardsClient.submitScore(getString(R.string.leaderboard_iniciados),
					mOutbox.mMaxPointsIniciado);
			mOutbox.mMaxPointsIniciado = -1;
		}
		if (mOutbox.mMaxPointsMaestro >= 0) {
			mLeaderboardsClient.submitScore(getString(R.string.leaderboard_maestros),
					mOutbox.mMaxPointsMaestro);
			mOutbox.mMaxPointsMaestro = -1;
		}
		/*if (mOutbox.mPrimeAchievement) {
			mAchievementsClient.unlock(getString(R.string.achievement_prime));
			mOutbox.mPrimeAchievement = false;
		}
		if (mOutbox.mArrogantAchievement) {
			mAchievementsClient.unlock(getString(R.string.achievement_arrogant));
			mOutbox.mArrogantAchievement = false;
		}
		if (mOutbox.mHumbleAchievement) {
			mAchievementsClient.unlock(getString(R.string.achievement_humble));
			mOutbox.mHumbleAchievement = false;
		}
		if (mOutbox.mLeetAchievement) {
			mAchievementsClient.unlock(getString(R.string.achievement_leet));
			mOutbox.mLeetAchievement = false;
		}
		if (mOutbox.mBoredSteps > 0) {
			mAchievementsClient.increment(getString(R.string.achievement_really_bored),
					mOutbox.mBoredSteps);
			mAchievementsClient.increment(getString(R.string.achievement_bored),
					mOutbox.mBoredSteps);
			mOutbox.mBoredSteps = 0;
		}
		if (mOutbox.mEasyModeScore >= 0) {
			mLeaderboardsClient.submitScore(getString(R.string.leaderboard_easy),
					mOutbox.mEasyModeScore);
			mOutbox.mEasyModeScore = -1;
		}
		if (mOutbox.mHardModeScore >= 0) {
			mLeaderboardsClient.submitScore(getString(R.string.leaderboard_hard),
					mOutbox.mHardModeScore);
			mOutbox.mHardModeScore = -1;
		}
		*/
	}

	/**
	 * Update leaderboards with the user's score.
	 *
	 * @param score The score the user got.
	 */
	public void updateLeaderboards(int score, String level) {
		// Según la dificultad
		//TODO: Actualizaríamos la tabla de puntuaciones correspondiente si es mayor que nuestra mejor marca
		Log.d("DEBUG", level);
		Log.d("DEBUG", getResources().getString(R.string.pref_level1_name));
		Log.d("DEBUG", getResources().getString(R.string.pref_level2_name));
		Log.d("DEBUG", getResources().getString(R.string.pref_level3_name));

		if(level.equals(getResources().getString(R.string.pref_level1_name)) && mOutbox.mMaxPointsAprendiz < score){
			//Actualizamos la tabla Aprendiz
			mOutbox.mMaxPointsAprendiz = score;
		}
		else{
			if(level.equals(getResources().getString(R.string.pref_level2_name)) && mOutbox.mMaxPointsIniciado < score){
				//Actualizamos Iniciado
				mOutbox.mMaxPointsIniciado = score;
			}
			else{
				if(level.equals(getResources().getString(R.string.pref_level3_name)) && mOutbox.mMaxPointsMaestro < score){
					//Actualizamos Maestro
					mOutbox.mMaxPointsMaestro = score;
				}
			}
		}

		/*
		if (mHardMode && mOutbox.mHardModeScore < finalScore) {
			mOutbox.mHardModeScore = finalScore;
		} else if (!mHardMode && mOutbox.mEasyModeScore < finalScore) {
			mOutbox.mEasyModeScore = finalScore;
		}
		*/
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);
		if (requestCode == RC_SIGN_IN) {
			Task<GoogleSignInAccount> task =
					GoogleSignIn.getSignedInAccountFromIntent(intent);

			try {
				GoogleSignInAccount account = task.getResult(ApiException.class);
				onConnected(account);
			} catch (ApiException apiException) {
				String message = apiException.getMessage();
				if (message == null || message.isEmpty()) {
					message = "SIGN_IN_ERROR";
				}

				onDisconnected();

				new android.app.AlertDialog.Builder(this)
						.setMessage(message)
						.setNeutralButton(android.R.string.ok, null)
						.show();
			}
		}
	}
	private void onConnected(GoogleSignInAccount googleSignInAccount) {
		Log.d(TAG, "onConnected(): connected to Google APIs");

		mAchievementsClient = Games.getAchievementsClient(this, googleSignInAccount);
		mLeaderboardsClient = Games.getLeaderboardsClient(this, googleSignInAccount);
		//mEventsClient = Games.getEventsClient(this, googleSignInAccount);
		mPlayersClient = Games.getPlayersClient(this, googleSignInAccount);

		// Set the greeting appropriately on main menu
		mPlayersClient.getCurrentPlayer()
				.addOnCompleteListener(new OnCompleteListener<Player>() {
					@Override
					public void onComplete(@NonNull Task<Player> task) {
						String displayName;
						if (task.isSuccessful()) {
							displayName = task.getResult().getDisplayName();
						} else {
							Exception e = task.getException();
							handleException(e, getString(R.string.players_exception));
							displayName = "???";
						}
						//mMainMenuFragment.setGreeting("Hello, " + displayName);
					}
				});


		// if we have accomplishments to push, push them
		if (!mOutbox.isEmpty()) {
			pushAccomplishments();
			Toast.makeText(this, getString(R.string.your_progress_will_be_uploaded),
					Toast.LENGTH_LONG).show();
		}

		//loadAndPrintEvents();
	}

	private void onDisconnected() {
		Log.d(TAG, "onDisconnected()");

		mAchievementsClient = null;
		mLeaderboardsClient = null;
		mPlayersClient = null;

		//mMainMenuFragment.setGreeting(getString(R.string.signed_out_greeting));
	}

	private class AccomplishmentsOutbox {
		// Almacenamiento temporal de los logros
		// TODO: Reemplazar
		boolean mHackerIniciadoAchievement = false;
		boolean mJuniorHackerAchievement = false;
		boolean mSeniorHackerAchievement = false;
		boolean mMasterHackerAchievement = false;
		boolean mCoderAchievement = false;
		boolean m25Achievement = false;
		boolean mNetSharkAchievement = false;
		boolean mThatsAFiftyAchievement = false;

		int mBoredSteps = 0;
		int mMaxPointsAprendiz = -1;
		int mMaxPointsIniciado = -1;
		int mMaxPointsMaestro = -1;

		boolean isEmpty() {
			return !mHackerIniciadoAchievement && !mJuniorHackerAchievement && !mSeniorHackerAchievement &&
					!mMasterHackerAchievement && !mCoderAchievement && !m25Achievement && !mNetSharkAchievement
					&& !mThatsAFiftyAchievement && mBoredSteps == 0 && mMaxPointsAprendiz == 0 &&
					mMaxPointsIniciado == 0 && mMaxPointsMaestro == 0;
		}

	}

}
