package com.nicesudoku.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import ui.GameScreen
import ui.SudokuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SudokuTheme {
                val viewModel: GameViewModel = viewModel()

                val gameState by viewModel.gameState.collectAsState()

                GameScreen(
                    gameState = gameState,
                    onCellClick = viewModel::onCellClick,
                    onNumberInput = viewModel::onNumberInput,
                    onEraseInput = viewModel::onEraseInput,
                    onTechniqueClick = viewModel::onTechniqueClick,
                    onFindTechniques = viewModel::onFindTechniques,
                    onApplyTechnique = viewModel::onApplyTechnique
                )
            }
        }

        // Load a sample puzzle on startup
        // This would normally be handled in the ViewModel
    }
}

