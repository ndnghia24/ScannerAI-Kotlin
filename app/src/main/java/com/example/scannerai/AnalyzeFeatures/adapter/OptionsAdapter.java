package com.example.scannerai.AnalyzeFeatures.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.scannerai.R;

import java.util.ArrayList;
import java.util.List;

public class OptionsAdapter extends RecyclerView.Adapter<OptionsAdapter.ViewHolder>{
    List<String> options = new ArrayList<>();
    int selectedPosition = 0;

    private OnOptionClickListener onOptionClickListener;

    public OptionsAdapter() {
        options.add("Object Detect GG-MLKIT");
        options.add("Image Label GG-MLKIT");
        options.add("Text Recognise GG-MLKIT");
    }

    public interface OnOptionClickListener {
        void onOptionClick(int position);
    }

    public void setOnOptionClickListener(OnOptionClickListener onOptionClickListener) {
        this.onOptionClickListener = onOptionClickListener;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.option_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        holder.optionButton.setText(options.get(position));
        if (selectedPosition == position) {
            holder.optionButton.setBackgroundResource(R.drawable.rounded_rectangle_selected);
        } else {
            holder.optionButton.setBackgroundResource(R.drawable.rounded_rectangle);
        }
        holder.optionButton.setOnClickListener(v -> {
            if (position != selectedPosition) {
                notifyItemChanged(selectedPosition);
                selectedPosition = position;
                notifyItemChanged(selectedPosition);
                onOptionClickListener.onOptionClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        Button optionButton;
        public ViewHolder(View itemView) {
            super(itemView);
            optionButton = itemView.findViewById(R.id.options_button);
        }
    }
}
