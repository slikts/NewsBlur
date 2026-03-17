package com.newsblur.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.newsblur.R;
import com.newsblur.database.BlurDatabaseHelper;
import com.newsblur.databinding.DialogTrainstoryBinding;
import com.newsblur.databinding.FragmentStoryIntelTrainerSheetBinding;
import com.newsblur.design.ReaderSheetPalette;
import com.newsblur.domain.Classifier;
import com.newsblur.domain.Story;
import com.newsblur.preference.PrefsRepo;
import com.newsblur.util.FeedSet;
import com.newsblur.util.FeedUtils;
import com.newsblur.util.NewsBlurBottomSheet;
import com.newsblur.util.UIUtils;
import com.newsblur.viewModel.StoryIntelTrainerViewModel;
import com.newsblur.viewModel.StoryIntelUiState;

import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class StoryIntelTrainerFragment extends BottomSheetDialogFragment {

    @Inject
    FeedUtils feedUtils;

    @Inject
    BlurDatabaseHelper dbHelper;

    @Inject
    PrefsRepo prefsRepo;

    private Story story;
    private FeedSet fs;
    private Classifier classifier;
    private Integer newTitleTraining;
    private DialogTrainstoryBinding contentBinding;
    private FragmentStoryIntelTrainerSheetBinding binding;

    private StoryIntelUiState latestState;
    private StoryIntelTrainerViewModel viewModel;

    public static StoryIntelTrainerFragment newInstance(Story story, FeedSet fs, @Nullable String selectedText) {
        if (story.feedId.equals("0")) {
            throw new IllegalArgumentException("cannot intel train stories with a null/zero feed");
        }
        StoryIntelTrainerFragment fragment = new StoryIntelTrainerFragment();
        Bundle args = new Bundle();
        args.putString("feedId", story.feedId);
        args.putString("storyHash", story.storyHash);
        args.putString("storyTitle", story.title);

        args.putSerializable("story", story);
        args.putSerializable("feedSet", fs);
        args.putString("selectedText", selectedText);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return NewsBlurBottomSheet.createDialog(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            NewsBlurBottomSheet.expandWithTheme(dialog, prefsRepo.getSelectedTheme());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStoryIntelTrainerSheetBinding.inflate(inflater, container, false);
        contentBinding = binding.trainContent;
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        story = (Story) getArguments().getSerializable("story");
        fs = (FeedSet) getArguments().getSerializable("feedSet");
        @Nullable String selectedText = getArguments().getString("selectedText");
        classifier = dbHelper.getClassifierForFeed(story.feedId);
        bindTheme();

        contentBinding.intelLoading.setVisibility(View.VISIBLE);
        contentBinding.intelContent.setVisibility(View.GONE);

        // get the viewmodel (Hilt-created Kotlin VM) using ViewModelProvider
        viewModel = new ViewModelProvider(this).get(StoryIntelTrainerViewModel.class);

        // observe LiveData UI state
        viewModel.getUiState().observe(this, uiState -> {
            latestState = uiState;
            renderUiState(uiState);
        });

        // set up the special title training box for the title from this story and the associated buttons
        contentBinding.intelTitleSelection.setText(story.title);
        // the layout sets inputType="none" on this EditText, but a widespread platform bug requires us
        // to also set this programmatically to make the field read-only for selection.
        contentBinding.intelTitleSelection.setInputType(android.text.InputType.TYPE_NULL);
        // the user is selecting for our custom widget, not to copy/paste
        contentBinding.intelTitleSelection.disableActionMenu();
        // pre-select the whole title to make it easier for the user to manipulate the selection handles
        contentBinding.intelTitleSelection.selectAll();
        // do this after init and selection to prevent toast spam
        contentBinding.intelTitleSelection.setForceSelection(true);
        // the disposition buttons for a new title training don't immediately impact the classifier object,
        // lest the user want to change selection substring after choosing the disposition.  so just store
        // the training factor in a variable that can be pulled on completion
        contentBinding.intelTitleLike.setOnClickListener(v1 -> {
            viewModel.setPendingTitleTraining(Classifier.LIKE);
            setLikeViews(contentBinding.intelTitleLike, contentBinding.intelTitleDislike);
        });
        contentBinding.intelTitleDislike.setOnClickListener(v2 -> {
            viewModel.setPendingTitleTraining(Classifier.DISLIKE);
            setDislikeViews(contentBinding.intelTitleLike, contentBinding.intelTitleDislike);
        });
        contentBinding.intelTitleClear.setOnClickListener(v3 -> {
            viewModel.setPendingTitleTraining(null);
            setClearViews(contentBinding.intelTitleLike, contentBinding.intelTitleDislike);
        });

        if (selectedText != null && !selectedText.isEmpty()) {
            // data
            contentBinding.intelTextSelection.setText(selectedText);
            contentBinding.intelTextSelection.setInputType(InputType.TYPE_NULL);
            contentBinding.intelTextSelection.disableActionMenu();
            contentBinding.intelTextSelection.selectAll();
            contentBinding.intelTextSelection.setForceSelection(true);

            // visibility
            contentBinding.intelTextHeader.setVisibility(View.VISIBLE);
            contentBinding.intelTextSelection.setVisibility(View.VISIBLE);
            contentBinding.intelTextClear.setVisibility(View.VISIBLE);
            contentBinding.intelTextLike.setVisibility(View.VISIBLE);
            contentBinding.intelTextDislike.setVisibility(View.VISIBLE);

            // disposition buttons
            contentBinding.intelTextLike.setOnClickListener(v4 -> {
                viewModel.setPendingTextTraining(Classifier.LIKE);
                setLikeViews(contentBinding.intelTextLike, contentBinding.intelTextDislike);
            });
            contentBinding.intelTextDislike.setOnClickListener(v5 -> {
                viewModel.setPendingTextTraining(Classifier.DISLIKE);
                setDislikeViews(contentBinding.intelTextLike, contentBinding.intelTextDislike);
            });
            contentBinding.intelTextClear.setOnClickListener(v6 -> {
                viewModel.setPendingTextTraining(null);
                setClearViews(contentBinding.intelTextLike, contentBinding.intelTextDislike);
            });
        } else {
            contentBinding.intelTextHeader.setVisibility(View.GONE);
            contentBinding.intelTextLike.setVisibility(View.GONE);
            contentBinding.intelTextDislike.setVisibility(View.GONE);
            contentBinding.intelTextClear.setVisibility(View.GONE);
            contentBinding.intelTextSelection.setVisibility(View.GONE);
        }

        binding.cancelButton.setOnClickListener(v7 -> dismiss());
        binding.saveButton.setOnClickListener(v8 -> saveAndDismiss());
    }

    private void saveAndDismiss() {
        if (latestState == null || latestState.getClassifier() == null) {
            return;
        }
        String textSelection =
                (contentBinding.intelTextSelection.getVisibility() == View.VISIBLE)
                        ? contentBinding.intelTextSelection.getSelection()
                        : null;

        Classifier updated = viewModel.buildUpdatedClassifier(
                latestState.getClassifier(),
                contentBinding.intelTitleSelection.getSelection(),
                textSelection
        );
        feedUtils.updateClassifier(story.feedId, updated, fs, requireActivity());
        dismiss();
    }

    private void bindTheme() {
        int borderColor = ReaderSheetPalette.borderArgb(prefsRepo.getSelectedTheme());
        int textPrimaryColor = ReaderSheetPalette.textPrimaryArgb(prefsRepo.getSelectedTheme());
        int textSecondaryColor = ReaderSheetPalette.textSecondaryArgb(prefsRepo.getSelectedTheme());
        int accentColor = ReaderSheetPalette.accentArgb(prefsRepo.getSelectedTheme());

        binding.sheetDragHandle.setBackground(makeRoundedRect(borderColor, 2f));
        binding.sheetTitle.setTextColor(textPrimaryColor);
        binding.cancelButton.setTextColor(textSecondaryColor);
        binding.cancelButton.setRippleColor(android.content.res.ColorStateList.valueOf(borderColor));
        binding.saveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        binding.saveButton.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
    }

    private android.graphics.drawable.GradientDrawable makeRoundedRect(int color, float radiusDp) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        drawable.setColor(color);
        return drawable;
    }

    @Override
    public void onDestroyView() {
        contentBinding = null;
        binding = null;
        super.onDestroyView();
    }

    private void setDislikeViews(View like, View dislike) {
        like.setBackgroundResource(R.drawable.ic_thumb_up_yellow);
        dislike.setBackgroundResource(R.drawable.ic_thumb_down_red);
    }

    private void setLikeViews(View like, View dislike) {
        like.setBackgroundResource(R.drawable.ic_thumb_up_green);
        dislike.setBackgroundResource(R.drawable.ic_thumb_down_yellow);
    }

    private void setClearViews(View like, View dislike) {
        like.setBackgroundResource(R.drawable.ic_thumb_up_yellow);
        dislike.setBackgroundResource(R.drawable.ic_thumb_down_yellow);
    }

    private void renderUiState(StoryIntelUiState state) {
        latestState = state;

        contentBinding.intelLoading.setVisibility(state.getLoading() ? View.VISIBLE : View.GONE);
        contentBinding.intelContent.setVisibility(state.getLoading() ? View.GONE : View.VISIBLE);

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_LONG).show();
        }

        // enable/disable Save
        if (binding != null) {
            binding.saveButton.setEnabled(!state.getLoading() && state.getClassifier() != null);
        }

        if (state.getLoading() || state.getClassifier() == null) return;

        Classifier c = state.getClassifier();

        // ----- Title matches -----
        contentBinding.existingTitleIntelContainer.removeAllViews();
        if (story.title != null) {
            for (Map.Entry<String, Integer> rule : c.title.entrySet()) {
                if (story.title.contains(rule.getKey())) {
                    View row = getLayoutInflater().inflate(R.layout.include_intel_row, contentBinding.existingTitleIntelContainer, false);
                    ((TextView) row.findViewById(R.id.intel_row_label)).setText(rule.getKey());
                    UIUtils.setupIntelDialogRow(row, c.title, rule.getKey());
                    contentBinding.existingTitleIntelContainer.addView(row);
                }
            }
        }

        // ----- Text matches -----
        contentBinding.existingTextIntelContainer.removeAllViews();
        String storyText = state.getStoryText();
        if (storyText != null) {
            String lower = storyText.toLowerCase(Locale.US);
            for (Map.Entry<String, Integer> rule : c.texts.entrySet()) {
                if (lower.contains(rule.getKey().toLowerCase(Locale.US))) {
                    View row = getLayoutInflater().inflate(R.layout.include_intel_row, contentBinding.existingTextIntelContainer, false);
                    ((TextView) row.findViewById(R.id.intel_row_label)).setText(rule.getKey());
                    UIUtils.setupIntelDialogRow(row, c.texts, rule.getKey());
                    contentBinding.existingTextIntelContainer.addView(row);
                }
            }

            if (contentBinding.intelTextHeader.getVisibility() == View.GONE &&
                    contentBinding.existingTextIntelContainer.getChildCount() > 0) {
                contentBinding.intelTextHeader.setVisibility(View.VISIBLE);
            }
        }

        // ----- Tags -----
        contentBinding.existingTagIntelContainer.removeAllViews();
        if (story.tags != null && story.tags.length > 0) {
            contentBinding.intelTagHeader.setVisibility(View.VISIBLE);
            for (String tag : story.tags) {
                View row = getLayoutInflater().inflate(R.layout.include_intel_row, contentBinding.existingTagIntelContainer, false);
                ((TextView) row.findViewById(R.id.intel_row_label)).setText(tag);
                UIUtils.setupIntelDialogRow(row, c.tags, tag);
                contentBinding.existingTagIntelContainer.addView(row);
            }
        } else {
            contentBinding.intelTagHeader.setVisibility(View.GONE);
        }

        // ----- Author -----
        contentBinding.existingAuthorIntelContainer.removeAllViews();
        if (!TextUtils.isEmpty(story.authors)) {
            contentBinding.intelAuthorHeader.setVisibility(View.VISIBLE);
            View row = getLayoutInflater().inflate(R.layout.include_intel_row, contentBinding.existingAuthorIntelContainer, false);
            ((TextView) row.findViewById(R.id.intel_row_label)).setText(story.authors);
            UIUtils.setupIntelDialogRow(row, c.authors, story.authors);
            contentBinding.existingAuthorIntelContainer.addView(row);
        } else {
            contentBinding.intelAuthorHeader.setVisibility(View.GONE);
        }

        // ----- Feed -----
        contentBinding.existingFeedIntelContainer.removeAllViews();
        View rowFeed = getLayoutInflater().inflate(R.layout.include_intel_row, contentBinding.existingFeedIntelContainer, false);
        ((TextView) rowFeed.findViewById(R.id.intel_row_label)).setText(feedUtils.getFeedTitle(story.feedId));
        UIUtils.setupIntelDialogRow(rowFeed, c.feeds, story.feedId);
        contentBinding.existingFeedIntelContainer.addView(rowFeed);
    }
}
