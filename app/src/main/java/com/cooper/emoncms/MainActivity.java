package com.cooper.emoncms;

import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.DrawerLayout;

public class MainActivity extends FragmentActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FragmentManager fragmentManager = getFragmentManager();
        NavigationDrawerFragment mNavigationDrawerFragment;
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                fragmentManager.findFragmentById(R.id.navigation_drawer);

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        fragmentManager.beginTransaction()
                .replace(R.id.container, new MainFragment(), getResources().getString(R.string.tag_main_fragment))
                .commit();
    }

    @Override
    public void onNavigationDrawerItemSelected(int position)
    {
        switch (position) {
            case 0:
                getFragmentManager().beginTransaction()
                        .replace(R.id.container, new MainFragment(), getResources().getString(R.string.tag_main_fragment))
                        .commit();
                break;
            case 1:
                getFragmentManager().beginTransaction()
                        .replace(R.id.container, new PrefFragment(), getResources().getString(R.string.tag_settings_fragment))
                        .commit();
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().findFragmentByTag(getResources().getString(R.string.tag_settings_fragment)) != null)
        {
            NavigationDrawerFragment navFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentByTag(getResources().getString(R.string.tag_navigation_fragment));
            navFragment.setSelectedItem(0);

            getFragmentManager().beginTransaction()
                    .replace(R.id.container, new MainFragment(), getResources().getString(R.string.tag_main_fragment))
                    .commit();
        }
        else
            super.onBackPressed();
    }
}