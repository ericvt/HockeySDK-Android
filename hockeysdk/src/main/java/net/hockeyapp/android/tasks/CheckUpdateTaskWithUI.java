package net.hockeyapp.android.tasks;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import net.hockeyapp.android.*;
import net.hockeyapp.android.utils.Util;
import net.hockeyapp.android.utils.VersionCache;

import org.json.JSONArray;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * <h3>Description</h3>
 *
 * Internal helper class. Checks if a new update is available by
 * fetching version data from Hockeyapp.
 *
 * <h3>License</h3>
 *
 * <pre>
 * Copyright (c) 2011-2014 Bit Stadium GmbH
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * </pre>
 *
 * @author Francisco Javier Fernandez
 **/
public class CheckUpdateTaskWithUI extends CheckUpdateTask {

  private Activity activity = null;
  private AlertDialog dialog = null;
  protected boolean isDialogRequired = false;

  public CheckUpdateTaskWithUI(WeakReference<Activity> weakActivity, String urlString, String appIdentifier, UpdateManagerListener listener, boolean isDialogRequired) {
    super(weakActivity, urlString, appIdentifier, listener);

    if (weakActivity != null) {
      activity = weakActivity.get();
    }

    this.isDialogRequired = isDialogRequired;
  }

  @Override
  public void detach() {
    super.detach();
    
    activity = null;
    
    if (dialog != null) {
      dialog.dismiss();
      dialog = null;
    }
  }

  @Override
  protected void onPostExecute(JSONArray updateInfo) {
    super.onPostExecute(updateInfo);
    if ((updateInfo != null) && (isDialogRequired)) {
      showDialog(updateInfo);
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void showDialog(final JSONArray updateInfo) {
    if (getCachingEnabled()) {
      VersionCache.setVersionInfo(activity, updateInfo.toString());
    }

    if ((activity == null) || (activity.isFinishing())) {
      return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setTitle(R.string.hockeyapp_update_dialog_title);

    if (!mandatory) {
      builder.setMessage(R.string.hockeyapp_update_dialog_message);
      builder.setNegativeButton(R.string.hockeyapp_update_dialog_negative_button, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          cleanUp();
          if (null != listener) {
            listener.onCancel();
          }
        }
      });

      builder.setPositiveButton(R.string.hockeyapp_update_dialog_positive_button, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          if (getCachingEnabled()) {
            VersionCache.setVersionInfo(activity, "[]");
          }

          WeakReference<Activity> weakActivity = new WeakReference<Activity>(activity);
          if ((Util.fragmentsSupported()) && (Util.runsOnTablet(weakActivity))) {
            showUpdateFragment(updateInfo);
          }
          else {
            startUpdateIntent(updateInfo, false);
          }
        }
      });

      dialog = builder.create();
      dialog.show();
    }
    else {
      String appName = Util.getAppName(activity);
      String toast = String.format(activity.getString(R.string.hockeyapp_update_mandatory_toast),
        appName);
      Toast.makeText(activity, toast, Toast.LENGTH_LONG).show();
      startUpdateIntent(updateInfo, true);
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void showUpdateFragment(final JSONArray updateInfo) {
    if (activity != null) {
      FragmentTransaction fragmentTransaction = activity.getFragmentManager().beginTransaction();
      fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

      Fragment existingFragment = activity.getFragmentManager().findFragmentByTag("hockey_update_dialog");
      if (existingFragment != null) {
        fragmentTransaction.remove(existingFragment);
      }
      fragmentTransaction.addToBackStack(null);

      // Create and show the dialog
      Class<? extends UpdateFragment> fragmentClass = UpdateFragment.class;
      if (listener != null) {
        fragmentClass = listener.getUpdateFragmentClass();
      }

      try {
        Method method = fragmentClass.getMethod("newInstance", JSONArray.class, String.class);
        DialogFragment updateFragment = (DialogFragment)method.invoke(null, updateInfo, getURLString("apk"));
        updateFragment.show(fragmentTransaction, "hockey_update_dialog");
      }
      catch (Exception e) {
        Log.d(Constants.TAG, "An exception happened while showing the update fragment:");
        e.printStackTrace();
        Log.d(Constants.TAG, "Showing update activity instead.");
        startUpdateIntent(updateInfo, false);
      }
    }
  }

  private void startUpdateIntent(final JSONArray updateInfo, Boolean finish) {
    Class<?> activityClass = null;
    if (listener != null) {
      activityClass = listener.getUpdateActivityClass();
    }
    if (activityClass == null) {
      activityClass = UpdateActivity.class;
    }

    if (activity != null) {
      Intent intent = new Intent();
      intent.setClass(activity, activityClass);
      intent.putExtra(INTENT_EXTRA_JSON, updateInfo.toString());
      intent.putExtra(INTENT_EXTRA_URL, getURLString(APK));
      activity.startActivity(intent);

      if (finish) {
        activity.finish();
      }
    }

    cleanUp();
  }

  @Override
  protected void cleanUp() {
    super.cleanUp();
    activity = null;
    dialog = null;
  }
}
