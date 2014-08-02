package net.somethingdreadful.MAL.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewFlipper;

import net.somethingdreadful.MAL.R;
import net.somethingdreadful.MAL.account.AccountService;
import net.somethingdreadful.MAL.tasks.AuthenticationCheckFinishedListener;
import net.somethingdreadful.MAL.tasks.AuthenticationCheckTask;

import org.holoeverywhere.app.AlertDialog;
import org.holoeverywhere.app.Dialog;
import org.holoeverywhere.app.DialogFragment;

public class UpdatePasswordDialogFragment extends DialogFragment implements AuthenticationCheckFinishedListener {
    EditText passwordEdit;
    TextView passwordWrongText;
    ViewFlipper viewFlipper;
    AlertDialog dialog;

    private View createView() {
        View result = getLayoutInflater().inflate(R.layout.dialog_update_password);
        passwordEdit = (EditText) result.findViewById(R.id.edittext_malPass);
        viewFlipper = (ViewFlipper) result.findViewById(R.id.viewFlipper);
        passwordWrongText = (TextView) result.findViewById(R.id.passwordWrongText);
        return result;
    }

    public String getPassword() {
        if (passwordEdit != null)
            return passwordEdit.getText().toString().trim();
        return null;
    }

    private void toggleLoadingIndicator(boolean show) {
        if (viewFlipper != null) {
            viewFlipper.setDisplayedChild(show ? 1 : 0);
        }
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!show);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(!show);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getSupportActivity(), getTheme());
        builder.setTitle(R.string.dialog_title_update_password);

        builder.setView(createView());

        builder.setPositiveButton(R.string.dialog_label_update, null); // < don't set the onClickListener here, we don't want to close the dialog immediately
        builder.setNegativeButton(R.string.dialog_label_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dismiss();
            }
        });
        dialog = builder.create();

        return dialog;
    }

    @Override
    public void onResume() {
        // set OnClickListener that does not close the dialog
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!getPassword().equals("")) {
                    passwordWrongText.setVisibility(View.GONE);
                    toggleLoadingIndicator(true);
                    new AuthenticationCheckTask(UpdatePasswordDialogFragment.this).execute(AccountService.getAccount(getActivity()).name, getPassword());
                }
            }
        });
        super.onResume();
    }

    @Override
    public void onAuthenticationCheckFinished(boolean result) {
        if (result) {
            AccountService.updatePassword(getActivity(), getPassword());
            dismiss();
        } else {
            passwordEdit.setText("");
            passwordWrongText.setVisibility(View.VISIBLE);
            toggleLoadingIndicator(false);
            passwordEdit.requestFocus();
        }
    }
}
